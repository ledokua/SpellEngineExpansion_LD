package net.ledok.spell_engine_expansion_ld.hud;

import net.minecraft.core.Holder;
import net.spell_engine.api.spell.Spell;

/// Duck interface on `SpellCooldownManager`: how long the HUD's cooldown sweep still has to run.
public interface CooldownView {
    /// Ticks until the spell can be cast again, from the same timers the sweep reads: the spell's own
    /// cooldown, its cooldown group, and for charged spells the recast lock or the recharge at 0 charges.
    /// 0 when the spell is ready.
    float seeld$ticksLeft(Holder<Spell> spell, float tickDelta);
}
