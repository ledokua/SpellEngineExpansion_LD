package net.ledok.spell_engine_expansion_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.spell_engine_expansion_ld.cast.CastTimeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.casting.SpellCast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Applies `cast_duration_deduct` / `cast_duration_multiply` to Spell Engine's cast-time queries:
/// the server's cast process and the client's prediction (`getCastTimeDetails`), tooltips
/// (`getCastDuration`), and the instant-cast check when the cast time reaches 0.
@Mixin(SpellParameters.class)
public abstract class SpellParametersMixin {
    @ModifyReturnValue(method = "getCastTimeDetails", at = @At("RETURN"))
    private static SpellCast.Duration seeld$castTimeDetails(SpellCast.Duration original, LivingEntity caster, Spell spell) {
        if (!(caster instanceof Player player) || original.length() <= 0 || !CastTimeModifiers.applies(spell)) {
            return original;
        }
        var entry = CastTimeModifiers.entryOf(player.level(), spell);
        if (entry == null) {
            return original;
        }
        float base = spell.active.cast.duration;
        float adjusted = CastTimeModifiers.adjust(player, entry, base);
        if (adjusted == base) {
            return original;
        }
        // `speed` is the haste the length was divided by, and the animation's playback speed: a shorter
        // cast plays its animation faster by the same ratio.
        float haste = original.speed() > 0 ? original.speed() : 1F;
        int length = Math.round(adjusted / haste * 20F);
        float speed = adjusted > 0 ? original.speed() * (base / adjusted) : original.speed();
        return new SpellCast.Duration(speed, length);
    }

    @ModifyReturnValue(
            method = "getCastDuration(Lnet/minecraft/world/entity/LivingEntity;Lnet/spell_engine/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)F",
            at = @At("RETURN"))
    private static float seeld$castDuration(float original, LivingEntity caster, Spell spell, ItemStack weapon) {
        if (!(caster instanceof Player player) || original <= 0 || !CastTimeModifiers.applies(spell)) {
            return original;
        }
        var entry = CastTimeModifiers.entryOf(player.level(), spell);
        if (entry == null) {
            return original;
        }
        float base = spell.active.cast.duration;
        return original * (CastTimeModifiers.adjust(player, entry, base) / base);
    }

    @ModifyReturnValue(method = "isInstantCast", at = @At("RETURN"))
    private static boolean seeld$instantWhenCastRemoved(boolean original, Holder<Spell> spellEntry, LivingEntity caster) {
        if (original || !(caster instanceof Player player) || !CastTimeModifiers.applies(spellEntry.value())) {
            return original;
        }
        return CastTimeModifiers.adjust(player, spellEntry, spellEntry.value().active.cast.duration) <= 0F;
    }
}
