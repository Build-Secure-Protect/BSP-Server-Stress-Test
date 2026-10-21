package com.mrgregles.bsp_server_stress_test.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every line of text the mod sends. The mod runs on the server only, so players' games do not have its language file and
 * would show a translation key as the raw key; the server reads the language file from its own jar instead and sends
 * plain text. The file is written by {@code tools/gen_lang.py}; {@code tools/check_lang.py} checks that every key used
 * here exists.
 */
public final class Text {
    public static final String PREFIX = "message." + BSPServerStressTest.MODID + ".";
    private static final String FILE = "/assets/" + BSPServerStressTest.MODID + "/lang/en_us.json";
    private static Map<String, String> lines = Map.of();

    private Text() {
    }

    public static void load() {
        Map<String, String> read = new HashMap<>();
        try (InputStream in = Text.class.getResourceAsStream(FILE)) {
            if (in == null) {
                BSPServerStressTest.LOGGER.error("The language file {} is missing from the jar", FILE);
            } else {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    read.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            BSPServerStressTest.LOGGER.error("Could not read the language file {}", FILE, e);
        }
        lines = Map.copyOf(read);
    }

    /** The English line for {@code message.bsp_server_stress_test.<key>} with its {@code %s} filled in; the key itself when missing. */
    public static String raw(String key, Object... args) {
        String pattern = lines.get(PREFIX + key);
        if (pattern == null) {
            return PREFIX + key;
        }
        return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
    }

    /** Whether the language file has {@code message.bsp_server_stress_test.<key>}. */
    public static boolean has(String key) {
        return lines.containsKey(PREFIX + key);
    }

    /** A line already made from {@link #raw} lines, as plain text for chat. */
    public static MutableComponent of(String line) {
        return Component.literal(line);
    }

    /** {@link #raw} as plain text for a chat line. */
    public static MutableComponent t(String key, Object... args) {
        return Component.literal(raw(key, args));
    }
}
