package net.ledok.spell_engine_expansion_ld.config;

import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;

/// `config/spell_engine_expansion_ld-client.json`: the spell hotbar overlay (client only, LeDok's request
/// of 2026-11-06). Written with defaults on first start; edit and restart the client.
public final class HudClientConfig {
    /// Draw the time left on a spell icon while it is on cooldown.
    public boolean cooldown_numbers = true;
    /// Below this many seconds the number shows one decimal ("4.2"), above it whole seconds ("13").
    public float cooldown_decimals_below = 10F;
    /// Draw a coloured frame around a spell icon while the spell is ready to cast.
    public boolean ready_border = true;
    /// Frame colour, "#RRGGBB".
    public String ready_border_color = "#FFCC33";

    private static HudClientConfig instance = new HudClientConfig();
    private static int borderColor = 0xFFFFCC33;

    public static HudClientConfig get() {
        return instance;
    }

    /// The frame colour as opaque ARGB.
    public static int borderColor() {
        return borderColor;
    }

    public static void load() {
        instance = JsonConfigFile.load(SpellEngineExpansionMod.ID + "-client.json", HudClientConfig.class, HudClientConfig::new);
        borderColor = parseColor(instance.ready_border_color, 0xFFFFCC33);
        if (!(instance.cooldown_decimals_below >= 0)) {
            instance.cooldown_decimals_below = 0;
        }
    }

    static int parseColor(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        var hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            SpellEngineExpansionMod.LOGGER.warn("ready_border_color {} is not #RRGGBB, using default", text);
            return fallback;
        }
    }
}
