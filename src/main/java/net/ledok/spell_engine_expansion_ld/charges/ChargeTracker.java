package net.ledok.spell_engine_expansion_ld.charges;

import net.ledok.spell_engine_expansion_ld.network.ChargeStatePayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.spell_engine.api.spell.Spell;

import java.util.List;
import java.util.Map;

/// Charge bookkeeping, implemented onto Spell Engine's `SpellCooldownManager` by `SpellCooldownManagerMixin`.
/// Reach it with `(ChargeTracker) ((SpellCaster.Player) player).getCooldownManager()`.
public interface ChargeTracker {
    Map<ResourceLocation, ChargeState> seeld$states();

    /// Charges the spell holds right now (its maximum minus the spent ones).
    int seeld$chargesLeft(Holder<Spell> spell);

    /// A cast went off: spend a charge, start the recharge if none runs, and lock recasting briefly.
    /// `progress` is the channel ratio for spells with a proportional cooldown.
    void seeld$consume(Holder<Spell> spell, float progress);

    /// Block casting for `ticks` without spending a charge (what Spell Engine's plain `set` becomes
    /// for a charged spell: the instant-cast global cooldown, `attempt_duration`).
    void seeld$lock(Holder<Spell> spell, int ticks, boolean force);

    /// Client: replace the state with the server's.
    void seeld$acceptSync(List<ChargeStatePayload.Entry> entries);

    /// Server: send the state to the owning player.
    void seeld$pushSync();
}
