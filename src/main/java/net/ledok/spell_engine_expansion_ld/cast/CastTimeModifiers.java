package net.ledok.spell_engine_expansion_ld.cast;

import net.ledok.spell_engine_expansion_ld.api.ModifierExtension;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellModifiers;
import org.jetbrains.annotations.Nullable;

/// `cast_duration_deduct` / `cast_duration_multiply` modifier fields. They shorten (or lengthen)
/// STANDARD casts only: a channel's duration is how long it lasts, a charge's how long it can be held.
public final class CastTimeModifiers {
    private CastTimeModifiers() { }

    public static boolean applies(@Nullable Spell spell) {
        return spell != null && spell.active != null && spell.active.cast != null
                && spell.active.cast.type == Spell.Active.Cast.Type.STANDARD
                && spell.active.cast.duration > 0;
    }

    /// Spell Engine's cast-time queries take a `Spell`, not its registry entry; modifiers are matched
    /// by id, so look the entry back up (a direct holder, not from the registry, has no modifiers).
    @Nullable
    public static Holder<Spell> entryOf(Level level, Spell spell) {
        var registry = SpellRegistry.from(level);
        var key = registry.getResourceKey(spell);
        if (key.isEmpty()) {
            return null;
        }
        return registry.getHolder(key.get()).map(holder -> (Holder<Spell>) holder).orElse(null);
    }

    /// `(base - sum(deduct)) * (1 + sum(multiply))`, never below 0; `base` is the spell's own cast
    /// duration in seconds, before haste.
    public static float adjust(Player player, Holder<Spell> spell, float baseSeconds) {
        float deduct = 0F;
        float multiply = 0F;
        for (var modifier : SpellModifiers.of(player, spell)) {
            var extension = (ModifierExtension) modifier;
            deduct += extension.seeld$castDurationDeduct();
            multiply += extension.seeld$castDurationMultiply();
        }
        if (deduct == 0F && multiply == 0F) {
            return baseSeconds;
        }
        return Math.max(0F, (baseSeconds - deduct) * Math.max(0F, 1F + multiply));
    }
}
