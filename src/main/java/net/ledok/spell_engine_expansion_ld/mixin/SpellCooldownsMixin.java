package net.ledok.spell_engine_expansion_ld.mixin;

import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.charges.SpellCharges;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfig;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfigs;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.cost.SpellCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// A successful cast of a charged spell spends a charge instead of starting a cooldown. The hosting
/// item lock (`spell_item_cooldown_lock`) is skipped too: recasting is the point of charges.
/// A recast root with `cooldown_start: END` gets no cooldown here; `RecastTracker` imposes it when the
/// chain ends.
@Mixin(SpellCooldowns.class)
public abstract class SpellCooldownsMixin {
    @Inject(method = "imposeCooldown", at = @At("HEAD"), cancellable = true)
    private static void seeld$spendCharge(Player player, SpellContainerSource.SourcedContainer source,
                                          Holder<Spell> spellEntry, float progress, CallbackInfo ci) {
        var recast = RecastConfigs.get(SpellCharges.id(spellEntry));
        if (recast != null && recast.cooldownStart() == RecastConfig.CooldownStart.END) {
            ci.cancel();
            return;
        }
        if (!SpellCharges.isCharged(player, spellEntry)) {
            return;
        }
        ci.cancel();
        ((ChargeTracker) ((SpellCaster.Player) player).getCooldownManager()).seeld$consume(spellEntry, progress);
    }
}
