package net.ledok.spell_engine_expansion_ld.charges;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ledok.spell_engine_expansion_ld.SpellDataFiles;
import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// The loaded charge table, keyed by spell id. The server reads `data/<ns>/spell_charges/**.json`;
/// the client gets a copy over the network (`ChargeConfigPayload`), so both sides answer the same.
///
/// File → spell id: `data/foo/spell_charges/wizards/frost_spikes.json` → `wizards:frost_spikes`
/// (the first folder is the spell's namespace); `data/wizards/spell_charges/frost_spikes.json` →
/// `wizards:frost_spikes`; a `"spell": "ns:id"` field in the JSON overrides both.
public final class ChargeConfigs {
    public static final String FOLDER = "spell_charges";

    private static Map<ResourceLocation, ChargeConfig> server = Map.of();
    private static Map<ResourceLocation, ChargeConfig> client = Map.of();

    private ChargeConfigs() { }

    @Nullable
    public static ChargeConfig get(Level level, ResourceLocation spellId) {
        return (level.isClientSide() ? client : server).get(spellId);
    }

    public static Map<ResourceLocation, ChargeConfig> serverTable() {
        return server;
    }

    public static void acceptSync(Map<ResourceLocation, ChargeConfig> table) {
        client = Map.copyOf(table);
    }

    public static void load(ResourceManager manager) {
        var table = new HashMap<ResourceLocation, ChargeConfig>();
        var files = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (var entry : files.entrySet()) {
            var fileId = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var spellId = spellIdOf(fileId, json);
                var previous = table.put(spellId, ChargeConfig.fromJson(json));
                if (previous != null) {
                    SpellEngineExpansionMod.LOGGER.warn("Spell charges for {} defined twice, {} wins", spellId, fileId);
                }
            } catch (Exception e) {
                SpellEngineExpansionMod.LOGGER.error("Bad spell charges file {}: {}", fileId, e.toString());
            }
        }
        server = Map.copyOf(table);
        SpellEngineExpansionMod.LOGGER.info("Loaded spell charges for {} spells", table.size());
    }

    static ResourceLocation spellIdOf(ResourceLocation fileId, JsonObject json) {
        return SpellDataFiles.spellIdOf(FOLDER, fileId, json);
    }
}
