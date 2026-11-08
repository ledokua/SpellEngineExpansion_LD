package net.ledok.spell_engine_expansion_ld.recast;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// One `spell_recast` file, keyed by the starting spell (the root):
/// `{"stages": ["ns:slam"], "window": 4.0, "cooldown_start": "END", "cancel_on_other_cast": false}`.
/// - `stages`: the follow-up spells in order (or a single `"recast": "ns:id"`). Normal ACTIVE spells;
///   they need no book or node, the root's hotbar slot casts them while the window is open.
/// - `window`: seconds to press the key again, restarted after each stage (default 4).
/// - `cooldown_start`: `FIRST` = the root's cooldown runs from its own cast; `END` (default) = it starts
///   when the last stage is cast or the window runs out.
/// - `cancel_on_other_cast`: casting any spell outside the chain closes the window (default false).
public record RecastConfig(List<ResourceLocation> stages, float window, CooldownStart cooldownStart, boolean cancelOnOtherCast) {
    public enum CooldownStart { FIRST, END }

    public static final float DEFAULT_WINDOW = 4F;

    public static RecastConfig fromJson(JsonObject json) {
        var stages = new ArrayList<ResourceLocation>();
        if (json.has("stages")) {
            for (var element : json.getAsJsonArray("stages")) {
                stages.add(ResourceLocation.parse(element.getAsString()));
            }
        } else if (json.has("recast")) {
            stages.add(ResourceLocation.parse(json.get("recast").getAsString()));
        }
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("missing \"stages\" (or \"recast\")");
        }
        float window = json.has("window") ? json.get("window").getAsFloat() : DEFAULT_WINDOW;
        if (window <= 0) {
            throw new IllegalArgumentException("\"window\" must be above 0");
        }
        var cooldownStart = json.has("cooldown_start")
                ? CooldownStart.valueOf(json.get("cooldown_start").getAsString().toUpperCase(Locale.ROOT))
                : CooldownStart.END;
        boolean cancel = json.has("cancel_on_other_cast") && json.get("cancel_on_other_cast").getAsBoolean();
        return new RecastConfig(List.copyOf(stages), window, cooldownStart, cancel);
    }
}
