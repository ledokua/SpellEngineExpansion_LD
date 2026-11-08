package net.ledok.spell_engine_expansion_ld.recast;

import com.google.gson.JsonParser;
import net.ledok.spell_engine_expansion_ld.SpellDataFiles;
import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// The loaded recast table (`data/<ns>/spell_recast/**.json`), keyed by root spell id. Server only:
/// the client learns the current stage of each open window from `RecastStatePayload`.
public final class RecastConfigs {
    public static final String FOLDER = "spell_recast";

    private static Map<ResourceLocation, RecastConfig> table = Map.of();

    private RecastConfigs() { }

    @Nullable
    public static RecastConfig get(ResourceLocation rootSpellId) {
        return table.get(rootSpellId);
    }

    public static Map<ResourceLocation, RecastConfig> table() {
        return table;
    }

    public static void load(ResourceManager manager) {
        var loaded = new HashMap<ResourceLocation, RecastConfig>();
        var files = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (var entry : files.entrySet()) {
            var fileId = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var spellId = SpellDataFiles.spellIdOf(FOLDER, fileId, json);
                var config = RecastConfig.fromJson(json);
                if (config.stages().contains(spellId)) {
                    throw new IllegalArgumentException("a spell can't be its own recast stage");
                }
                if (loaded.put(spellId, config) != null) {
                    SpellEngineExpansionMod.LOGGER.warn("Spell recast for {} defined twice, {} wins", spellId, fileId);
                }
            } catch (Exception e) {
                SpellEngineExpansionMod.LOGGER.error("Bad spell recast file {}: {}", fileId, e.toString());
            }
        }
        table = Map.copyOf(loaded);
        SpellEngineExpansionMod.LOGGER.info("Loaded spell recasts for {} spells", loaded.size());
    }
}
