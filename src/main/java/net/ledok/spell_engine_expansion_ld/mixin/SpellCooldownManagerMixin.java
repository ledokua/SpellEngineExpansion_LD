package net.ledok.spell_engine_expansion_ld.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.spell_engine_expansion_ld.charges.ChargeState;
import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.charges.SpellCharges;
import net.ledok.spell_engine_expansion_ld.hud.CooldownView;
import net.ledok.spell_engine_expansion_ld.network.ChargeStatePayload;
import net.ledok.spell_engine_expansion_ld.network.RecastStatePayload;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfig;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfigs;
import net.ledok.spell_engine_expansion_ld.recast.RecastState;
import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.spell_engine.internals.SpellParameters;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.cost.SpellCooldownManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Charges live next to Spell Engine's cooldowns, in its cooldown manager. For a charged spell every
/// cooldown query is answered from the charge state instead of the cooldown entries:
/// - `getCooldownProgress` (what `isCoolingDown`, the cast gate and the HUD overlay read) is 0 while a
///   charge is left, the recharge progress at 0 charges, and the recast-lock progress right after a cast;
/// - `set` becomes a recast lock (no charge spent): Spell Engine calls it for the instant-cast global
///   cooldown and for `attempt_duration`; a real cast spends its charge through `SpellCooldownsMixin`;
/// - `getCooldownDuration` / `setDurationLeft` (the COOLDOWN impact) read and move the recharge timer;
///   a reset (`duration_multiplier: 0`) hands back one charge, not all.
/// Cooldown groups (`cost.cooldown.group`) keep their "one at a time" rule: a running group cooldown
/// blocks the charged spell too, and a cast of the charged spell puts its group on cooldown for the
/// recast lock only, so the group mates wait half a second, not the whole recharge.
@Mixin(SpellCooldownManager.class)
public abstract class SpellCooldownManagerMixin implements ChargeTracker, RecastTracker, CooldownView {
    @Shadow private int tick;
    @Shadow @Final private Map<ResourceLocation, SpellCooldownManager.Entry> entries;
    @Shadow @Final private Entity owner;
    @Shadow protected abstract float getCooldownProgress(ResourceLocation spell, float tickDelta);
    @Shadow protected abstract void set(ResourceLocation spell, int duration, boolean force);
    @Shadow public abstract void set(Holder<Spell> spell, int duration, boolean force);
    @Shadow public abstract void pushSync();
    @Shadow @Nullable private static ResourceLocation groupId(Spell spell) { throw new AssertionError(); }

    @Unique private static final String SEELD_NBT_KEY = "seeld_spell_charges";
    @Unique private final Map<ResourceLocation, ChargeState> seeld$states = new HashMap<>();
    @Unique private final Map<ResourceLocation, RecastState> seeld$recasts = new HashMap<>();

    // MARK: HUD view

    @Override
    public float seeld$ticksLeft(Holder<Spell> spell, float tickDelta) {
        float now = tick + tickDelta;
        var groupId = groupId(spell.value());
        float left = groupId != null ? seeld$entryLeft(groupId, now) : 0F;
        var player = seeld$player();
        if (player != null && SpellCharges.isCharged(player, spell)) {
            // mirrors seeld$chargeProgress: the lock and the recharge at 0 charges are the sweep
            var state = seeld$states.get(SpellCharges.id(spell));
            if (state != null) {
                if (state.lockActive(now)) {
                    left = Math.max(left, state.lockEnd - now);
                }
                if (state.missing >= SpellCharges.maxCharges(player, spell) && state.recharging) {
                    left = Math.max(left, state.rechargeEnd - now);
                }
            }
        } else {
            left = Math.max(left, seeld$entryLeft(SpellCharges.id(spell), now));
        }
        return Math.max(0F, left);
    }

    @Unique
    private float seeld$entryLeft(ResourceLocation id, float now) {
        var entry = entries.get(id);
        return entry == null ? 0F : entry.endTick() - now;
    }

    // MARK: Queries Spell Engine makes

    @Inject(method = "getCooldownProgress(Lnet/minecraft/core/Holder;F)F", at = @At("HEAD"), cancellable = true)
    private void seeld$chargeProgress(Holder<Spell> spell, float tickDelta, CallbackInfoReturnable<Float> cir) {
        var player = seeld$player();
        if (player == null || !SpellCharges.isCharged(player, spell)) {
            return;
        }
        // The group's own cooldown (started by a group mate, or by this spell's recast lock) still counts
        var groupId = groupId(spell.value());
        float progress = groupId != null ? getCooldownProgress(groupId, tickDelta) : 0F;
        var state = seeld$states.get(SpellCharges.id(spell));
        if (state != null) {
            float now = tick + tickDelta;
            if (state.lockActive(now)) {
                progress = Math.max(progress, seeld$ratio(state.lockStart, state.lockEnd, now));
            }
            if (state.missing >= SpellCharges.maxCharges(player, spell) && state.recharging) {
                progress = Math.max(progress, seeld$ratio(state.rechargeStart, state.rechargeEnd, now));
            }
        }
        cir.setReturnValue(progress);
    }

    @Inject(method = "getCooldownDuration(Lnet/minecraft/core/Holder;)I", at = @At("HEAD"), cancellable = true)
    private void seeld$rechargeLeft(Holder<Spell> spell, CallbackInfoReturnable<Integer> cir) {
        if (!seeld$isCharged(spell)) {
            return;
        }
        var state = seeld$states.get(SpellCharges.id(spell));
        cir.setReturnValue(state != null && state.missing > 0 && state.recharging
                ? Math.max(0, state.rechargeEnd - tick)
                : 0);
    }

    @Inject(method = "setDurationLeft(Lnet/minecraft/core/Holder;I)V", at = @At("HEAD"), cancellable = true)
    private void seeld$moveRecharge(Holder<Spell> spell, int duration, CallbackInfo ci) {
        if (!seeld$isCharged(spell)) {
            return;
        }
        ci.cancel();
        var state = seeld$states.get(SpellCharges.id(spell));
        if (state == null || state.missing <= 0) {
            return;
        }
        if (duration <= 0) {
            seeld$restoreOne(SpellCharges.id(spell), state);
        } else {
            state.recharging = true;
            state.rechargeStart = tick;
            state.rechargeEnd = tick + duration;
        }
    }

    @Inject(method = "set(Lnet/minecraft/core/Holder;IZ)V", at = @At("HEAD"), cancellable = true)
    private void seeld$setAsLock(Holder<Spell> spell, int duration, boolean force, CallbackInfo ci) {
        if (!seeld$isCharged(spell)) {
            return;
        }
        ci.cancel();
        seeld$lock(spell, duration, force);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void seeld$reset(@Nullable ResourceLocation spellId, CallbackInfo ci) {
        if (spellId == null) {
            seeld$states.clear();
        } else {
            seeld$states.remove(spellId);
        }
        // Spell Engine's reset ends with pushSync(), which carries our state along
    }

    // MARK: Lifecycle

    @Inject(method = "tickUpdate", at = @At("TAIL"))
    private void seeld$tickCharges(CallbackInfo ci) {
        if (seeld$player() == null) {
            return;
        }
        if (!seeld$recasts.isEmpty()) {
            for (var root : List.copyOf(seeld$recasts.keySet())) {
                if (tick >= seeld$recasts.get(root).windowEnd) {
                    seeld$closeRecast(root);
                }
            }
        }
        if (seeld$states.isEmpty()) {
            return;
        }
        boolean restored = false;
        var iterator = seeld$states.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var state = entry.getValue();
            if (state.missing > 0 && state.recharging && tick >= state.rechargeEnd) {
                seeld$restoreOne(entry.getKey(), state);
                restored = true;
            }
            if (state.idle(tick)) {
                iterator.remove();
            }
        }
        if (restored) {
            seeld$pushSync();
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void seeld$writeNbt(CompoundTag nbt, CallbackInfo ci) {
        if (seeld$states.isEmpty()) {
            return;
        }
        var root = new CompoundTag();
        for (var entry : seeld$states.entrySet()) {
            var state = entry.getValue();
            var tag = new CompoundTag();
            tag.putInt("missing", state.missing);
            tag.putBoolean("recharging", state.recharging);
            tag.putInt("recharge_start", state.rechargeStart - tick);
            tag.putInt("recharge_end", state.rechargeEnd - tick);
            tag.putInt("lock_start", state.lockStart - tick);
            tag.putInt("lock_end", state.lockEnd - tick);
            root.put(entry.getKey().toString(), tag);
        }
        nbt.put(SEELD_NBT_KEY, root);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void seeld$readNbt(CompoundTag nbt, CallbackInfo ci) {
        seeld$states.clear();
        if (!nbt.contains(SEELD_NBT_KEY)) {
            return;
        }
        var root = nbt.getCompound(SEELD_NBT_KEY);
        for (var key : root.getAllKeys()) {
            var id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            var tag = root.getCompound(key);
            var state = new ChargeState();
            state.missing = tag.getInt("missing");
            state.recharging = tag.getBoolean("recharging");
            state.rechargeStart = tick + tag.getInt("recharge_start");
            state.rechargeEnd = tick + tag.getInt("recharge_end");
            state.lockStart = tick + tag.getInt("lock_start");
            state.lockEnd = tick + tag.getInt("lock_end");
            seeld$states.put(id, state);
        }
    }

    @Inject(method = "pushSync", at = @At("TAIL"))
    private void seeld$pushSyncTail(CallbackInfo ci) {
        seeld$pushSync();
    }

    // MARK: ChargeTracker

    @Override
    public Map<ResourceLocation, ChargeState> seeld$states() {
        return seeld$states;
    }

    @Override
    public int seeld$chargesLeft(Holder<Spell> spell) {
        var player = seeld$player();
        if (player == null) {
            return 0;
        }
        int max = SpellCharges.maxCharges(player, spell);
        var state = seeld$states.get(SpellCharges.id(spell));
        return state == null ? max : Math.max(0, max - state.missing);
    }

    @Override
    public void seeld$consume(Holder<Spell> spell, float progress) {
        var player = seeld$player();
        if (player == null) {
            return;
        }
        var state = seeld$states.computeIfAbsent(SpellCharges.id(spell), id -> new ChargeState());
        if (state.missing < SpellCharges.maxCharges(player, spell)) {
            state.missing++;
        }
        if (!state.recharging && state.missing > 0) {
            seeld$startRecharge(spell, state, SpellCharges.proportional(spell) ? progress : 1F);
        }
        int lock = SpellCharges.lockTicks(player, spell);
        if (lock > 0) {
            state.lockStart = tick;
            state.lockEnd = tick + lock;
        }
        var groupId = groupId(spell.value());
        if (groupId != null && lock > 0) {
            // Group mates wait out the recast lock. The client learns group entries only from the full
            // sync (it drops per-id packets for ids that aren't spells), which carries our state too.
            set(groupId, lock, true);
            if (owner instanceof ServerPlayer) {
                pushSync();
                return;
            }
        }
        seeld$pushSync();
    }

    @Override
    public void seeld$lock(Holder<Spell> spell, int ticks, boolean force) {
        if (ticks <= 0 && !force) {
            return;
        }
        var state = seeld$states.computeIfAbsent(SpellCharges.id(spell), id -> new ChargeState());
        int end = tick + Math.max(0, ticks);
        if (force || end > state.lockEnd) {
            state.lockStart = tick;
            state.lockEnd = end;
        }
        seeld$pushSync();
    }

    @Override
    public void seeld$acceptSync(List<ChargeStatePayload.Entry> entries) {
        seeld$states.clear();
        for (var entry : entries) {
            seeld$states.put(entry.spell(), entry.toState(tick));
        }
    }

    @Override
    public void seeld$pushSync() {
        if (owner instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, ChargeStatePayload.TYPE)) {
            ServerPlayNetworking.send(serverPlayer, ChargeStatePayload.of(tick, seeld$states));
            ServerPlayNetworking.send(serverPlayer, RecastStatePayload.of(tick, seeld$recasts));
        }
    }

    // MARK: RecastTracker

    @Override
    public Map<ResourceLocation, RecastState> seeld$recasts() {
        return seeld$recasts;
    }

    @Override
    public int seeld$currentTick() {
        return tick;
    }

    @Override
    @Nullable
    public ResourceLocation seeld$rootOfStage(ResourceLocation spellId) {
        for (var entry : seeld$recasts.entrySet()) {
            if (entry.getValue().stage.equals(spellId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void seeld$openRecast(Holder<Spell> root, RecastConfig config) {
        int window = Math.max(1, Math.round(config.window() * 20F));
        seeld$recasts.put(SpellCharges.id(root), new RecastState(config.stages().getFirst(), 0, tick, tick + window));
        seeld$pushSync();
    }

    @Override
    public void seeld$advanceRecast(ResourceLocation root) {
        var state = seeld$recasts.get(root);
        var config = RecastConfigs.get(root);
        if (state == null || config == null || state.stageIndex + 1 >= config.stages().size()) {
            seeld$closeRecast(root);
            return;
        }
        state.stageIndex++;
        state.stage = config.stages().get(state.stageIndex);
        state.windowStart = tick;
        state.windowEnd = tick + Math.max(1, Math.round(config.window() * 20F));
        seeld$pushSync();
    }

    @Override
    public void seeld$closeRecast(ResourceLocation root) {
        if (seeld$recasts.remove(root) == null) {
            return;
        }
        var config = RecastConfigs.get(root);
        if (config != null && config.cooldownStart() == RecastConfig.CooldownStart.END
                && owner instanceof ServerPlayer serverPlayer) {
            SpellRegistry.from(serverPlayer.level()).getHolder(root).ifPresent(rootEntry -> {
                int ticks = Math.round(SpellParameters.getCooldownDuration(serverPlayer, rootEntry) * 20F);
                if (ticks > 0) {
                    set(rootEntry, ticks, true); // a charged root: this becomes its recast lock
                }
            });
        }
        seeld$pushSync();
    }

    @Override
    public void seeld$acceptRecastSync(List<RecastStatePayload.Entry> entries) {
        seeld$recasts.clear();
        for (var entry : entries) {
            seeld$recasts.put(entry.root(), entry.toState(tick));
        }
    }

    // MARK: Helpers

    @Unique
    @Nullable
    private Player seeld$player() {
        return owner instanceof Player player ? player : null;
    }

    @Unique
    private boolean seeld$isCharged(Holder<Spell> spell) {
        var player = seeld$player();
        return player != null && SpellCharges.isCharged(player, spell);
    }

    @Unique
    private static float seeld$ratio(int start, int end, float now) {
        float total = Math.max(1, end - start);
        return Math.min(1F, Math.max(0F, (end - now) / total));
    }

    /// One charge comes back. The server restarts the timer while charges are still missing (the
    /// duration is re-read, so a node unlocked meanwhile counts); the client waits for the server's state.
    @Unique
    private void seeld$restoreOne(ResourceLocation id, ChargeState state) {
        state.missing = Math.max(0, state.missing - 1);
        state.recharging = false;
        if (state.missing > 0 && owner instanceof ServerPlayer serverPlayer) {
            SpellRegistry.from(serverPlayer.level()).getHolder(id)
                    .ifPresent(spell -> seeld$startRecharge(spell, state, 1F));
        }
    }

    @Unique
    private void seeld$startRecharge(Holder<Spell> spell, ChargeState state, float factor) {
        var player = seeld$player();
        if (player == null) {
            return;
        }
        int ticks = Math.round(SpellCharges.rechargeTicks(player, spell) * factor);
        if (ticks <= 0) {
            // No recharge time: nothing is ever missing
            state.missing = 0;
            state.recharging = false;
            return;
        }
        state.recharging = true;
        state.rechargeStart = tick;
        state.rechargeEnd = tick + ticks;
    }
}
