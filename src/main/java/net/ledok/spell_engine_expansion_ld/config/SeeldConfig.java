package net.ledok.spell_engine_expansion_ld.config;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;

/// `config/spell_engine_expansion_ld.json`. Written with defaults when missing; unknown keys are kept
/// out, known keys that are missing take their default. Only the binding-table behaviour is switched
/// here: charges, recasts and the modifier fields do nothing until data files use them.
/// The HUD options live in the client file, see [HudClientConfig].
public final class SeeldConfig {
    /// Unbinding a spell at the binding table gives one scroll of it back.
    public boolean unbind_refunds_scroll = true;
    /// Spells bound with lapis (a `learn` offer, no scroll in the slot) are marked on the book and give no
    /// scroll back. Off = every unbind refunds, whatever paid for the bind.
    public boolean mark_lapis_bound_spells = true;

    private static SeeldConfig instance = new SeeldConfig();

    public static SeeldConfig get() {
        return instance;
    }

    public static void load() {
        instance = JsonConfigFile.load(SpellEngineExpansionMod.ID + ".json", SeeldConfig.class, SeeldConfig::new);
    }
}
