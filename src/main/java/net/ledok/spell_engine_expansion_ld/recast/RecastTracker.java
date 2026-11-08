package net.ledok.spell_engine_expansion_ld.recast;

import net.ledok.spell_engine_expansion_ld.network.RecastStatePayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/// Open recast windows of one caster, kept on Spell Engine's `SpellCooldownManager` next to the charges
/// (`SpellCooldownManagerMixin`). Reach it with `(RecastTracker) ((SpellCaster.Player) player).getCooldownManager()`.
public interface RecastTracker {
    /// Root spell id → its open window.
    Map<ResourceLocation, RecastState> seeld$recasts();

    int seeld$currentTick();

    /// The root whose open window currently points at `spellId`, or null when `spellId` is no pending stage.
    @Nullable ResourceLocation seeld$rootOfStage(ResourceLocation spellId);

    /// The root fired: open its window at the first stage (a window already open starts over).
    void seeld$openRecast(Holder<Spell> root, RecastConfig config);

    /// The current stage fired: move to the next stage, or end the chain after the last one.
    void seeld$advanceRecast(ResourceLocation root);

    /// End the chain (last stage cast, window expired, another spell cast). With `cooldown_start: END`
    /// the server puts the root on cooldown now.
    void seeld$closeRecast(ResourceLocation root);

    void seeld$acceptRecastSync(List<RecastStatePayload.Entry> entries);
}
