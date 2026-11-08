package net.ledok.spell_engine_expansion_ld.mixin;

import net.ledok.spell_engine_expansion_ld.api.ModifierExtension;
import net.spell_engine.api.spell.Spell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/// Adds JSON fields to `Spell.Modifier`. Spell Engine deserializes (and syncs) spells with Gson, which
/// reads any instance field of the class, so a field added here is a field in the MODIFIER spell's JSON.
@Mixin(Spell.Modifier.class)
public abstract class SpellModifierMixin implements ModifierExtension {
    @Unique public int charges_add;
    @Unique public float cast_duration_deduct;
    @Unique public float cast_duration_multiply;

    @Override
    public int seeld$chargesAdd() {
        return charges_add;
    }

    @Override
    public float seeld$castDurationDeduct() {
        return cast_duration_deduct;
    }

    @Override
    public float seeld$castDurationMultiply() {
        return cast_duration_multiply;
    }
}
