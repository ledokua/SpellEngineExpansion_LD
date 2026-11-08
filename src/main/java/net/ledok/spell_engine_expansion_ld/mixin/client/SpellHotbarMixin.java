package net.ledok.spell_engine_expansion_ld.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.casting.ClientCastController;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/// While a root's recast window is open, its hotbar slot becomes the current stage: icon, cast mode,
/// targeting, cast time and the spell the key sends are all the stage's.
@Mixin(SpellHotbar.class)
public abstract class SpellHotbarMixin {
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/spell_engine/client/casting/ClientCastController;displayOptions()Ljava/util/List;"))
    private List<SpellCast.Option> seeld$swapRecastStages(ClientCastController controller, Operation<List<SpellCast.Option>> original,
                                                          LocalPlayer player, Options options) {
        var declared = original.call(controller);
        var recasts = ((RecastTracker) ((SpellCaster.Player) player).getCooldownManager()).seeld$recasts();
        if (recasts.isEmpty() || declared.isEmpty()) {
            return declared;
        }
        var registry = SpellRegistry.from(player.level());
        var swapped = new ArrayList<SpellCast.Option>(declared.size());
        for (var option : declared) {
            var state = recasts.get(option.id());
            if (state != null) {
                var stage = registry.getHolder(state.stage).orElse(null);
                if (stage != null && stage.value().active != null) {
                    swapped.add(SpellCast.Option.of(player, stage));
                    continue;
                }
            }
            swapped.add(option);
        }
        return swapped;
    }
}
