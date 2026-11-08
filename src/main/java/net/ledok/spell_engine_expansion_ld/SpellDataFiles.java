package net.ledok.spell_engine_expansion_ld;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/// Per-spell data files live in a folder under any data namespace and are keyed by spell id:
/// `data/foo/<folder>/wizards/frost_spikes.json` → `wizards:frost_spikes` (the first folder is the spell's
/// namespace); `data/wizards/<folder>/frost_spikes.json` → `wizards:frost_spikes`; a `"spell": "ns:id"`
/// field in the JSON overrides both.
public final class SpellDataFiles {
    private SpellDataFiles() { }

    public static ResourceLocation spellIdOf(String folder, ResourceLocation fileId, JsonObject json) {
        if (json.has("spell")) {
            return ResourceLocation.parse(json.get("spell").getAsString());
        }
        var path = fileId.getPath();
        path = path.substring(folder.length() + 1, path.length() - ".json".length());
        int slash = path.indexOf('/');
        if (slash < 0) {
            return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), path);
        }
        return ResourceLocation.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
    }
}
