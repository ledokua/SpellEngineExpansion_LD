package net.ledok.spell_engine_expansion_ld.recast;

import net.ledok.spell_engine_expansion_ld.charges.SpellCharges;
import net.minecraft.resources.ResourceLocation;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;

import java.util.List;

/// Drives recast windows from Spell Engine's cast event, which fires once a spell's delivery succeeded
/// and its cost was taken: a root opens its window, a pending stage advances it, any other spell closes
/// the windows flagged `cancel_on_other_cast`.
public final class RecastEvents {
    private RecastEvents() { }

    public static void register() {
        SpellEvents.SPELL_CAST.register(args -> {
            if (args.action() != SpellCast.Action.RELEASE || args.caster().level().isClientSide()) {
                return;
            }
            var player = args.caster();
            var spellId = SpellCharges.id(args.spell());
            var tracker = (RecastTracker) ((SpellCaster.Player) player).getCooldownManager();
            ResourceLocation own = null;
            var root = tracker.seeld$rootOfStage(spellId);
            if (root != null) {
                tracker.seeld$advanceRecast(root);
                own = root;
            } else {
                var config = RecastConfigs.get(spellId);
                if (config != null) {
                    tracker.seeld$openRecast(args.spell(), config);
                    own = spellId;
                }
            }
            for (var openRoot : List.copyOf(tracker.seeld$recasts().keySet())) {
                if (openRoot.equals(own)) {
                    continue;
                }
                var config = RecastConfigs.get(openRoot);
                if (config != null && config.cancelOnOtherCast()) {
                    tracker.seeld$closeRecast(openRoot);
                }
            }
        });
    }
}
