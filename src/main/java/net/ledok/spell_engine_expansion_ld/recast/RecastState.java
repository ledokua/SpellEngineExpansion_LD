package net.ledok.spell_engine_expansion_ld.recast;

import net.minecraft.resources.ResourceLocation;

/// An open recast window of one root spell: which stage the root's slot casts now, and until when,
/// in the ticks of the caster's `SpellCooldownManager`.
public final class RecastState {
    public ResourceLocation stage;
    public int stageIndex;
    public int windowStart;
    public int windowEnd;

    public RecastState(ResourceLocation stage, int stageIndex, int windowStart, int windowEnd) {
        this.stage = stage;
        this.stageIndex = stageIndex;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public float remaining(float now) {
        float total = Math.max(1, windowEnd - windowStart);
        return Math.min(1F, Math.max(0F, (windowEnd - now) / total));
    }
}
