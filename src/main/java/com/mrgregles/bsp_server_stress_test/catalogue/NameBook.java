package com.mrgregles.bsp_server_stress_test.catalogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import net.minecraft.locale.Language;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The English names of blocks and mobs, for the catalogue and the reports ("Digital Miner", not
 * {@code block.mekanism.digital_miner}). A dedicated server does not load the mods' language files, so the names are
 * read from {@code assets/<namespace>/lang/en_us.json} inside each loaded mod's jar, once, keeping only the keys asked
 * for; Minecraft's own come from the server's language.
 */
public final class NameBook {
    private final Map<String, String> names;

    private NameBook(Map<String, String> names) {
        this.names = names;
    }

    /** Reads every loaded mod's English language file, keeping the keys {@code wanted} accepts. */
    public static NameBook read(Predicate<String> wanted) {
        Map<String, String> out = new HashMap<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            try {
                Path assets = info.getFile().findResource("assets");
                if (!Files.isDirectory(assets)) {
                    continue;
                }
                try (Stream<Path> namespaces = Files.list(assets)) {
                    for (Path ns : (Iterable<Path>) namespaces::iterator) {
                        Path lang = ns.resolve("lang").resolve("en_us.json");
                        if (Files.isRegularFile(lang)) {
                            readInto(lang, wanted, out);
                        }
                    }
                }
            } catch (Exception e) {
                BSPServerStressTest.LOGGER.debug("Could not read language files of {}: {}", info.getFile().getFileName(), e.toString());
            }
        }
        return new NameBook(out);
    }

    private static void readInto(Path lang, Predicate<String> wanted, Map<String, String> out) {
        try (Reader r = Files.newBufferedReader(lang, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject json = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                if (e.getValue().isJsonPrimitive() && wanted.test(e.getKey())) {
                    out.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            BSPServerStressTest.LOGGER.debug("Could not read {}: {}", lang, e.toString());
        }
    }

    /** The English for a key, from the mods' files or the server's language; {@code fallback} when neither has it. */
    public String name(String key, String fallback) {
        String found = names.get(key);
        if (found != null) {
            return found;
        }
        Language language = Language.getInstance();
        return language.has(key) ? language.getOrDefault(key) : fallback;
    }
}
