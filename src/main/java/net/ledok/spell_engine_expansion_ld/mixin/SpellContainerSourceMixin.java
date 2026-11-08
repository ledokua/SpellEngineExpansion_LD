package net.ledok.spell_engine_expansion_ld.mixin;

import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Spell Engine's possession gate (cast request and `performSpell` both ask "which container grants this
/// spell?"). A recast stage is granted by nobody; while its root's window is open it is cast from the
/// root's source.
@Mixin(SpellContainerSource.class)
public abstract class SpellContainerSourceMixin {
    @Inject(method = "getFirstSourceOfSpell", at = @At("HEAD"), cancellable = true)
    private static void seeld$stageFromRootSource(ResourceLocation spellId, Player player,
                                                  CallbackInfoReturnable<SpellContainerSource.SourcedContainer> cir) {
        var tracker = (RecastTracker) ((SpellCaster.Player) player).getCooldownManager();
        if (tracker.seeld$recasts().isEmpty()) {
            return;
        }
        var root = tracker.seeld$rootOfStage(spellId);
        if (root != null && !root.equals(spellId)) {
            cir.setReturnValue(SpellContainerSource.getFirstSourceOfSpell(root, player));
        }
    }
}
