package net.ledok.spell_engine_expansion_ld.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.spell_engine_expansion_ld.SpellEngineExpansionMod;

import java.nio.file.Files;
import java.util.function.Supplier;

/// One Gson-backed JSON file under `config/`: read if present, defaults for missing keys, then written
/// back so the file always shows every key.
public final class JsonConfigFile {
    private JsonConfigFile() { }

    public static <T> T load(String fileName, Class<T> type, Supplier<T> defaults) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var path = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        var loaded = defaults.get();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                var parsed = gson.fromJson(JsonParser.parseReader(reader), type);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                SpellEngineExpansionMod.LOGGER.error("Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(loaded));
        } catch (Exception e) {
            SpellEngineExpansionMod.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
        return loaded;
    }
}
