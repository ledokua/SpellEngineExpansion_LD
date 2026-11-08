package net.ledok.spell_engine_expansion_ld.charges;

/// Per-spell charge state of one caster, in the ticks of their `SpellCooldownManager`.
/// `missing` counts spent charges (so a raised maximum hands the new charge out at once);
/// one recharge timer runs while any charge is missing; the lock is the pause between two casts.
public final class ChargeState {
    public int missing;
    public boolean recharging;
    public int rechargeStart;
    public int rechargeEnd;
    public int lockStart;
    public int lockEnd;

    public boolean lockActive(float now) {
        return lockEnd > now;
    }

    public boolean idle(int tick) {
        return missing <= 0 && !recharging && !lockActive(tick);
    }
}
