package net.ledok.spell_engine_expansion_ld.mixin;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.ledok.spell_engine_expansion_ld.binding.ScrollRefund;
import net.ledok.spell_engine_expansion_ld.config.SeeldConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/// Unbinding at the binding table gives the scroll back. The unbind decision is taken here, before Spell
/// Engine's own button handling (which only removes the spell): a click on a spell the book already holds
/// removes it, hands the player one scroll of it, and plays the unbind sound. Levels and lapis spent on
/// binding are not refunded. A spell bound with lapis instead of a scroll is marked on the book and gives
/// no scroll back (`mark_lapis_bound_spells`).
@Mixin(SpellBindingScreenHandler.class)
public abstract class SpellBindingScreenHandlerMixin extends AbstractContainerMenu {
    @Shadow @Final private Container inventory;
    @Shadow @Final private ContainerLevelAccess context;

    @Unique private boolean seeld$captured = false;
    @Unique private Set<String> seeld$spellsBefore = Set.of();
    @Unique private boolean seeld$scrollInSlot = false;
    @Unique private boolean seeld$handledUnbind = false;

    protected SpellBindingScreenHandlerMixin() {
        super(null, 0);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void seeld$unbindWithRefund(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        seeld$handledUnbind = false;
        seeld$captured = false;
        var self = (SpellBindingScreenHandler) (Object) this;
        var config = SeeldConfig.get();
        if (!config.unbind_refunds_scroll || !self.allowUnbinding()
                || id == SpellBindingScreenHandler.INIT_SYNC_ID || id < 0 || id >= SpellBindingScreenHandler.MAXIMUM_SPELL_COUNT
                || self.mode[0] != SpellBinding.Mode.SPELL.ordinal()) {
            return;
        }
        var mainStack = this.getItems().get(0);
        var container = SpellContainerHelper.containerFromItemStack(mainStack);
        if (container == null || !container.isValid()) {
            return;
        }
        // Remember what a bind adds, to mark lapis-bound spells (see the RETURN injection)
        seeld$captured = true;
        seeld$spellsBefore = new HashSet<>(container.spell_ids());
        seeld$scrollInSlot = this.getItems().get(1).is(SpellEngineItemTags.SPELL_BOOK_MERGEABLE);

        int rawId = self.spellId[id];
        if (rawId < 0) {
            return;
        }
        var entry = SpellRegistry.from(player.level()).getHolder(rawId).orElse(null);
        if (entry == null) {
            return;
        }
        var spellId = entry.key().location();
        if (!container.spell_ids().contains(spellId.toString())) {
            return; // not bound: Spell Engine's bind path runs
        }
        seeld$handledUnbind = true;
        cir.setReturnValue(true);
        this.context.execute((world, pos) -> {
            SpellContainerHelper.removeSpell(world, spellId, mainStack);
            boolean refund = !ScrollRefund.isMarkedNoRefund(mainStack, spellId);
            ScrollRefund.unmark(mainStack, spellId);
            if (refund) {
                ScrollRefund.give(player, ScrollRefund.scrollOf(world, entry));
            }
            SpellEngineExpansionMod.LOGGER.info("[Spells] {} unbound {} from {}: {}", player.getScoreboardName(), spellId,
                    mainStack.getHoverName().getString(), refund ? "its scroll went back to them" : "no scroll (bound with lapis)");
            this.inventory.setChanged();
            this.slotsChanged(this.inventory);
            world.playSound(null, pos, SpellEngineSounds.UNBIND_SPELL.soundEvent(), SoundSource.BLOCKS, 1.0F,
                    world.getRandom().nextFloat() * 0.1F + 0.9F);
        });
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void seeld$markLapisBound(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!seeld$captured || seeld$handledUnbind) {
            return;
        }
        seeld$captured = false;
        if (!SeeldConfig.get().mark_lapis_bound_spells || seeld$scrollInSlot || !cir.getReturnValue()) {
            return;
        }
        var mainStack = this.getItems().get(0);
        var container = SpellContainerHelper.containerFromItemStack(mainStack);
        if (container == null) {
            return;
        }
        for (var idString : container.spell_ids()) {
            if (!seeld$spellsBefore.contains(idString)) {
                var spellId = ResourceLocation.tryParse(idString);
                if (spellId != null) {
                    ScrollRefund.markNoRefund(mainStack, spellId);
                }
            }
        }
    }
}
