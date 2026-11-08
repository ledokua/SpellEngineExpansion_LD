package net.ledok.spell_engine_expansion_ld;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.ledok.spell_engine_expansion_ld.api.ModifierExtension;
import net.ledok.spell_engine_expansion_ld.cast.CastTimeModifiers;
import net.ledok.spell_engine_expansion_ld.charges.ChargeConfigs;
import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.charges.SpellCharges;
import net.ledok.spell_engine_expansion_ld.config.SeeldConfig;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfigs;
import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.casting.SpellCaster;

import java.util.Locale;

/// What the server sees: a one-line summary in the log after the spell registry loads, and `/seeld`
/// (op level 2) to print a spell's charges and the modifier fields that reach it for the caller, or to
/// put a spell on cooldown for HUD tests.
public final class Diagnostics {
    private Diagnostics() { }

    public static void logSummary(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(SpellRegistry.KEY);
        int modifierSpells = 0;
        for (var spell : registry) {
            if (spell.modifiers == null) {
                continue;
            }
            for (var modifier : spell.modifiers) {
                var extension = (ModifierExtension) modifier;
                if (extension.seeld$chargesAdd() != 0 || extension.seeld$castDurationDeduct() != 0
                        || extension.seeld$castDurationMultiply() != 0) {
                    modifierSpells++;
                    break;
                }
            }
        }
        SpellEngineExpansionMod.LOGGER.info("Spell data: {} spell_charges files, {} spell_recast files, {} of {} spells carry charges_add / cast-time modifier fields",
                ChargeConfigs.serverTable().size(), RecastConfigs.table().size(), modifierSpells, registry.size());
        var config = SeeldConfig.get();
        SpellEngineExpansionMod.LOGGER.info("Config: unbind_refunds_scroll={}, mark_lapis_bound_spells={}",
                config.unbind_refunds_scroll, config.mark_lapis_bound_spells);
        for (var entry : RecastConfigs.table().entrySet()) {
            for (var stage : entry.getValue().stages()) {
                if (!registry.containsKey(stage)) {
                    SpellEngineExpansionMod.LOGGER.warn("Spell recast {}: stage spell {} does not exist", entry.getKey(), stage);
                }
            }
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("seeld")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("charges")
                        .then(Commands.argument("spell", ResourceArgument.resource(registryAccess, SpellRegistry.KEY))
                                .executes(Diagnostics::printCharges)))
                .then(Commands.literal("modifiers")
                        .then(Commands.argument("spell", ResourceArgument.resource(registryAccess, SpellRegistry.KEY))
                                .executes(Diagnostics::printModifiers)))
                .then(Commands.literal("recast")
                        .then(Commands.argument("spell", ResourceArgument.resource(registryAccess, SpellRegistry.KEY))
                                .executes(Diagnostics::printRecast)))
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("spell", ResourceArgument.resource(registryAccess, SpellRegistry.KEY))
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0F, 3600F))
                                        .executes(Diagnostics::setCooldown)))));
    }

    /// `/seeld cooldown <spell> <seconds>`: puts the caller's spell on cooldown (0 clears it). For testing
    /// the HUD without casting; a charged spell gets its recast lock, like after a cast.
    private static int setCooldown(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        Holder<Spell> spell = ResourceArgument.getResource(context, "spell", SpellRegistry.KEY);
        float seconds = FloatArgumentType.getFloat(context, "seconds");
        var manager = ((SpellCaster.Player) player).getCooldownManager();
        manager.set(spell, Math.round(seconds * 20F), true);
        manager.pushSync();
        var text = String.format(Locale.ROOT, "%s: cooldown set to %.1fs", SpellCharges.id(spell), seconds);
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int printCharges(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        Holder<Spell> spell = ResourceArgument.getResource(context, "spell", SpellRegistry.KEY);
        var id = SpellCharges.id(spell);
        var config = SpellCharges.config(player, spell);
        if (!SpellCharges.isCharged(player, spell)) {
            context.getSource().sendSuccess(() -> Component.literal(id + ": not a charged spell (no spell_charges file, no charges_add)"), false);
            return 1;
        }
        var tracker = (ChargeTracker) ((SpellCaster.Player) player).getCooldownManager();
        var state = tracker.seeld$states().get(id);
        var text = String.format(Locale.ROOT,
                "%s: %d/%d charges (file: %s, charges_add: %d), recharge %.1fs, recast lock %.2fs%s",
                id, tracker.seeld$chargesLeft(spell), SpellCharges.maxCharges(player, spell),
                config == null ? "none" : config.charges() + (config.rechargeFromCooldown() ? ", recharge = cooldown" : ", recharge " + config.recharge() + "s"),
                SpellCharges.chargesAdd(player, spell),
                SpellCharges.rechargeTicks(player, spell) / 20F,
                SpellCharges.lockTicks(player, spell) / 20F,
                state == null ? "" : String.format(Locale.ROOT, " | missing %d, recharging %s", state.missing, state.recharging));
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int printRecast(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        Holder<Spell> spell = ResourceArgument.getResource(context, "spell", SpellRegistry.KEY);
        var id = SpellCharges.id(spell);
        var config = RecastConfigs.get(id);
        var tracker = (RecastTracker) ((SpellCaster.Player) player).getCooldownManager();
        var builder = new StringBuilder(id.toString());
        if (config == null) {
            builder.append(": no spell_recast file");
            var root = tracker.seeld$rootOfStage(id);
            if (root != null) {
                builder.append(", currently the open stage of ").append(root);
            }
        } else {
            builder.append(String.format(Locale.ROOT, ": stages %s, window %.1fs, cooldown_start %s, cancel_on_other_cast %s",
                    config.stages(), config.window(), config.cooldownStart(), config.cancelOnOtherCast()));
            var state = tracker.seeld$recasts().get(id);
            builder.append(state == null ? " | window closed" : String.format(Locale.ROOT, " | window open: stage %s, %.1fs left",
                    state.stage, Math.max(0, state.windowEnd - tracker.seeld$currentTick()) / 20F));
        }
        var text = builder.toString();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int printModifiers(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        Holder<Spell> spell = ResourceArgument.getResource(context, "spell", SpellRegistry.KEY);
        var modifiers = SpellModifiers.of(player, spell);
        var builder = new StringBuilder(SpellCharges.id(spell) + ": " + modifiers.size() + " modifier(s) apply");
        for (var modifier : modifiers) {
            var extension = (ModifierExtension) modifier;
            builder.append(String.format(Locale.ROOT, "\n  pattern %s: charges_add %d, cast_duration_deduct %.2f, cast_duration_multiply %.2f, cooldown_duration_deduct %.2f",
                    modifier.spell_pattern, extension.seeld$chargesAdd(), extension.seeld$castDurationDeduct(),
                    extension.seeld$castDurationMultiply(), modifier.cooldown_duration_deduct));
        }
        if (CastTimeModifiers.applies(spell.value())) {
            float base = spell.value().active.cast.duration;
            builder.append(String.format(Locale.ROOT, "\n  cast time %.2fs -> %.2fs (before haste)", base, CastTimeModifiers.adjust(player, spell, base)));
        }
        var text = builder.toString();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
