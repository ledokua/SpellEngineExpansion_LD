package net.ledok.spell_engine_expansion_ld.charges;

import net.ledok.spell_engine_expansion_ld.api.ModifierExtension;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.SpellParameters;
import org.jetbrains.annotations.Nullable;

/// The numbers behind a charged spell for one player: whether it is charged at all, its maximum,
/// and how long a recharge and the recast lock take. Pure reads, so the client HUD and the server agree.
public final class SpellCharges {
    private SpellCharges() { }

    public static ResourceLocation id(Holder<Spell> spell) {
        return spell.unwrapKey().orElseThrow().location();
    }

    @Nullable
    public static ChargeConfig config(Player player, Holder<Spell> spell) {
        return ChargeConfigs.get(player.level(), id(spell));
    }

    /// A spell is charged when it has a `spell_charges` file, or when a modifier gives it `charges_add`.
    public static boolean isCharged(Player player, Holder<Spell> spell) {
        return config(player, spell) != null || chargesAdd(player, spell) > 0;
    }

    public static int maxCharges(Player player, Holder<Spell> spell) {
        var config = config(player, spell);
        int base = config != null ? config.charges() : 1;
        return Math.max(1, base + chargesAdd(player, spell));
    }

    public static int chargesAdd(Player player, Holder<Spell> spell) {
        int sum = 0;
        for (var modifier : SpellModifiers.of(player, spell)) {
            sum += ((ModifierExtension) modifier).seeld$chargesAdd();
        }
        return sum;
    }

    /// Ticks one charge takes to return: the file's `recharge` (or the spell's cooldown duration),
    /// minus `cooldown_duration_deduct` modifiers, divided by haste when the cooldown is haste affected.
    public static int rechargeTicks(Player player, Holder<Spell> spell) {
        var config = config(player, spell);
        var cooldown = spell.value().cost != null ? spell.value().cost.cooldown : null;
        float seconds = config != null && !config.rechargeFromCooldown()
                ? config.recharge()
                : (cooldown != null ? cooldown.duration : 0F);
        seconds -= SpellModifiers.cooldownDeduction(player, spell);
        if (seconds > 0 && SpellEngineMod.config.haste_affects_cooldown && (cooldown == null || cooldown.haste_affected)) {
            seconds = SpellParameters.hasteAffectedValue(player, spell.value().school, seconds);
        }
        return Math.max(0, Math.round(seconds * 20F));
    }

    public static int lockTicks(Player player, Holder<Spell> spell) {
        var config = config(player, spell);
        float seconds = config != null ? config.recastLock() : ChargeConfig.DEFAULT_RECAST_LOCK;
        return Math.max(0, Math.round(seconds * 20F));
    }

    public static boolean proportional(Holder<Spell> spell) {
        var cost = spell.value().cost;
        return cost != null && cost.cooldown != null && cost.cooldown.proportional;
    }
}
