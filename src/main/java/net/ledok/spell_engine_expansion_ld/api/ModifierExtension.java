package net.ledok.spell_engine_expansion_ld.api;

/// Fields this mod adds to `Spell.Modifier` (see `SpellModifierMixin`). Spell Engine parses spell JSON
/// with Gson, so the added fields are read straight from the MODIFIER spell's JSON and travel to the
/// client with the spell registry sync. All of them are summed across the modifiers that match a spell.
public interface ModifierExtension {
    /// Extra charges for the matched spells (`charges_add`). A spell without a `spell_charges` file
    /// becomes a charged spell with `1 + charges_add` charges.
    int seeld$chargesAdd();

    /// Seconds taken off the cast time (`cast_duration_deduct`), before haste.
    float seeld$castDurationDeduct();

    /// Cast time multiplier bonus (`cast_duration_multiply`): `-0.25` = 25% shorter, `0` = unchanged.
    /// The final cast time is `(duration - deduct) * (1 + multiply)`, never below 0.
    float seeld$castDurationMultiply();
}
