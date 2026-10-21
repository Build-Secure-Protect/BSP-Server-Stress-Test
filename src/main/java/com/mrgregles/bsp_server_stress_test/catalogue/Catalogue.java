package com.mrgregles.bsp_server_stress_test.catalogue;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.Profile;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything this server can be stress tested with (owner decisions D5, D6): built at server start from the mods it
 * loaded, plus the general options every server has. Machines are the blocks whose block entity ticks on the server;
 * a wall form of a block (its item places the standing one) is not listed apart; mobs are the summonable entity types
 * outside the MISC category; explosives are TNT and its kin and the blocks whose
 * id or class name ends in tnt, bomb, nuke, explosive or dynamite (a guess by name: the settings' {@code leaveOut}
 * corrects a wrong one); working setups are the curated ones of each loaded mod family. Each entry is ready (a runnable
 * option exists), coming with a later task, or left out with the reason. The full list is written to
 * {@code config/bsp_server_stress_test/options.txt}.
 */
public final class Catalogue {
    public static final String FILE = "options.txt";
    /** An id or class name that ends in one of these words (tnt, deepslate_bomb, NukeBlock, BlockExplosive), not one that only starts with it (explosive_crafter). */
    private static final Pattern EXPLOSIVE_END = Pattern.compile("(?i)(tnt|bomb|nuke|explosive|dynamite)$");
    private static List<Entry> entries = List.of();
    private static NameBook book;

    public enum Kind {
        GENERAL("kind.general"), MACHINE("kind.machine"), MOB("kind.mob"), EXPLOSIVE("kind.explosive"), SETUP("kind.setup");

        final String key;

        Kind(String key) {
            this.key = key;
        }

        public String text() {
            return Text.raw(key);
        }
    }

    /**
     * One thing to test with. {@code note} is the task that builds it, or for a left-out entry the reason;
     * {@link #ready()} asks the options registry, so an entry becomes ready the moment its option exists.
     */
    public record Entry(String id, Kind kind, String modId, String name, boolean leftOut, String note) {
        public boolean ready() {
            return !leftOut && Options.byId(id) != null;
        }

        public String state() {
            if (leftOut) {
                return Text.raw("catalogue.state.left_out", note);
            }
            return ready() ? Text.raw("catalogue.state.ready") : Text.raw("catalogue.state.planned", note);
        }
    }

    private Catalogue() {
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static Entry byId(String id) {
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** The entries a profile would include (left-out ones never). */
    public static List<Entry> chosenBy(Profile profile) {
        return entries.stream().filter(e -> !e.leftOut && profile.includes(e.id)).toList();
    }

    /** Builds the catalogue from the registries and writes the file. Called at server start and by {@code /stress options}. */
    public static List<Entry> build(MinecraftServer server) {
        List<Entry> out = new ArrayList<>(general());
        Set<Block> machines = tickingBlocks(server.overworld());
        Set<String> keys = new LinkedHashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (isVariant(b)) {
                continue; // a wall sign or wall torch: the same item places the standing block, which is listed
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            keys.add(b.getDescriptionId());
            if (isExplosive(b, id)) {
                out.add(blockEntry("explosions", Kind.EXPLOSIVE, b, id, "ST-13"));
            } else if (machines.contains(b)) {
                out.add(blockEntry("machines", Kind.MACHINE, b, id, "ST-11"));
            }
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            keys.add(type.getDescriptionId());
            if (type.getCategory() != MobCategory.MISC && type.canSummon()) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                out.add(new Entry("entities." + id.getNamespace() + "." + id.getPath(), Kind.MOB, id.getNamespace(),
                        type.getDescriptionId(), leftOutByList(id), leftOutByList(id) ? Text.raw("catalogue.reason.listed") : "ST-12"));
            }
        }
        List<com.mrgregles.bsp_server_stress_test.setups.SetupOption> setups = com.mrgregles.bsp_server_stress_test.setups.Setups.available();
        for (var s : setups) {
            out.add(new Entry(s.id(), Kind.SETUP, s.modId(), s.name(), false, "ST-15"));
        }
        book = NameBook.read(keys::contains);
        List<Entry> named = new ArrayList<>();
        for (Entry e : out) {
            named.add(e.kind == Kind.GENERAL || e.kind == Kind.SETUP ? e : new Entry(e.id, e.kind, e.modId, book.name(e.name, idOf(e)), e.leftOut, e.note));
        }
        entries = List.copyOf(named);
        // one runnable option per offered machine (idle, placed in the grid at the places) and per mob (a crowd)
        Options.unregisterPrefix("machines.");
        Options.unregisterPrefix("entities.");
        Options.unregisterPrefix("explosions.");
        Options.unregisterPrefix("setups.");
        setups.forEach(Options::register);
        Options.register(new com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption(
                com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption.VANILLA,
                com.mrgregles.bsp_server_stress_test.explosions.Explosions.Kind.VANILLA, null, ""));
        Options.register(new com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption(
                com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption.TNT_CHAIN,
                com.mrgregles.bsp_server_stress_test.explosions.Explosions.Kind.TNT_CHAIN, null, ""));
        for (Entry e : entries) {
            if (e.kind == Kind.EXPLOSIVE && !e.leftOut) {
                ResourceLocation id = ResourceLocation.tryParse(idOf(e));
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    Options.register(new com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption(e.id,
                            com.mrgregles.bsp_server_stress_test.explosions.Explosions.Kind.BLOCK, BuiltInRegistries.BLOCK.get(id), e.name));
                }
            }
        }
        Options.register(com.mrgregles.bsp_server_stress_test.entities.EntityOption.items());
        Options.register(com.mrgregles.bsp_server_stress_test.entities.EntityOption.xp());
        for (Entry e : entries) {
            if (e.kind == Kind.MOB && !e.leftOut) {
                ResourceLocation id = ResourceLocation.tryParse(idOf(e));
                if (id != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> Options.register(
                            new com.mrgregles.bsp_server_stress_test.entities.EntityOption(e.id,
                                    com.mrgregles.bsp_server_stress_test.entities.Spawns.Kind.MOB, type, e.name)));
                }
            }
        }
        for (Entry e : entries) {
            if (e.kind == Kind.MACHINE && !e.leftOut) {
                ResourceLocation id = ResourceLocation.tryParse(idOf(e));
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    Options.register(new com.mrgregles.bsp_server_stress_test.machines.MachineOption(e.id, BuiltInRegistries.BLOCK.get(id), e.name));
                }
            }
        }
        write();
        return entries;
    }

    /** The English name of a block or an entity type by id, for reports; the id itself when none is known. */
    public static String nameOf(ResourceLocation id, boolean block) {
        String key = block ? BuiltInRegistries.BLOCK.get(id).getDescriptionId()
                : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescriptionId).orElse("");
        return book == null || key.isEmpty() ? id.toString() : book.name(key, id.toString());
    }

    private static String idOf(Entry e) {
        String rest = e.id.substring(e.id.indexOf('.') + 1);
        return rest.replaceFirst("\\.", ":");
    }

    private static Entry blockEntry(String prefix, Kind kind, Block b, ResourceLocation id, String task) {
        String reason = null;
        if (leftOutByList(id)) {
            reason = Text.raw("catalogue.reason.listed");
        } else if (b.asItem() == Items.AIR) {
            reason = Text.raw("catalogue.reason.no_item");
        }
        return new Entry(prefix + "." + id.getNamespace() + "." + id.getPath(), kind, id.getNamespace(), b.getDescriptionId(),
                reason != null, reason != null ? reason : task);
    }

    /** Every block whose block entity has a ticker on the server. */
    static Set<Block> tickingBlocks(ServerLevel level) {
        Set<Block> out = new LinkedHashSet<>();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            for (Block b : type.validBlocks) {
                if (out.contains(b) || !(b instanceof EntityBlock eb)) {
                    continue;
                }
                try {
                    if (eb.getTicker(level, b.defaultBlockState(), type) != null) {
                        out.add(b);
                    }
                } catch (Throwable t) {
                    BSPServerStressTest.LOGGER.debug("Ticker check failed for {}: {}", BuiltInRegistries.BLOCK.getKey(b), t.toString());
                }
            }
        }
        return out;
    }

    /** A block whose item places another block (a wall sign's item is the sign): listed under that block instead. */
    static boolean isVariant(Block b) {
        Item item = b.asItem();
        return item != Items.AIR && Block.byItem(item) != b;
    }

    static boolean isExplosive(Block b, ResourceLocation id) {
        if (b instanceof TntBlock) {
            return true;
        }
        List<String> classes = new ArrayList<>();
        for (Class<?> c = b.getClass(); c != null && c != Block.class && c != Object.class; c = c.getSuperclass()) {
            classes.add(c.getSimpleName());
        }
        return looksExplosive(id.getPath(), classes);
    }

    /** The guess by name: the id's path, or one of the block's class names without "Block", ends in an explosive word. */
    public static boolean looksExplosive(String path, List<String> classNames) {
        if (EXPLOSIVE_END.matcher(path).find()) {
            return true;
        }
        for (String c : classNames) {
            if (EXPLOSIVE_END.matcher(c.replaceFirst("^Block", "").replaceFirst("Block$", "")).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean leftOutByList(ResourceLocation id) {
        for (String s : StressConfig.LEAVE_OUT.get()) {
            String p = s.trim();
            if (p.equals(id.toString()) || (p.endsWith(":*") && p.substring(0, p.length() - 2).equals(id.getNamespace()))) {
                return true;
            }
        }
        return false;
    }

    /** The options every server has, whatever its mods; each built by the task named. */
    static List<Entry> general() {
        String[][] g = {
                {"bots.stand", "ST-9"}, {"bots.wander", "ST-10"}, {"bots.fly_out", "ST-10"},
                {"chunks.hold", "ST-7"}, {"chunks.generate_fresh", "ST-7"},
                {"entities.items", "ST-12"}, {"entities.xp", "ST-12"},
                {"explosions.vanilla", "ST-13"}, {"explosions.tnt_chain", "ST-13"},
                {"world.redstone_clocks", "ST-14"}, {"world.hoppers", "ST-14"}, {"world.fluids", "ST-14"},
                {"world.random_ticks", "ST-14"}, {"world.lighting", "ST-14"}, {"world.falling_blocks", "ST-14"},
                {"world.save", "ST-14"}, {"test.spin", "ST-2"},
        };
        List<Entry> out = new ArrayList<>();
        for (String[] e : g) {
            out.add(new Entry(e[0], Kind.GENERAL, "", Text.raw("general." + e[0].replace('.', '_')), false, e[1]));
        }
        return out;
    }

    /** Entries grouped by mod (general ones first, under ""), mods in the order of their display names. */
    public static Map<String, List<Entry>> byMod() {
        Map<String, List<Entry>> out = new LinkedHashMap<>();
        out.put("", new ArrayList<>());
        entries.stream()
                .sorted(Comparator.comparing((Entry e) -> e.modId.isEmpty() ? "" : modName(e.modId).toLowerCase(Locale.ROOT))
                        .thenComparing(Entry::kind).thenComparing(Entry::id))
                .forEach(e -> out.computeIfAbsent(e.modId, k -> new ArrayList<>()).add(e));
        return out;
    }

    public static String modName(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    /** Writes {@code options.txt}: every entry by mod, what it is, and whether it is ready, coming, or left out. */
    public static Path write() {
        Path file = StressFiles.file(FILE);
        List<String> lines = new ArrayList<>();
        long ready = entries.stream().filter(Entry::ready).count();
        long left = entries.stream().filter(Entry::leftOut).count();
        lines.add(Text.raw("catalogue.file.title", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT))));
        lines.add(Text.raw("catalogue.file.how"));
        lines.add(Text.raw("catalogue.file.counts", ready, entries.size() - ready - left, left));
        int width = entries.stream().mapToInt(e -> e.id.length()).max().orElse(10) + 2;
        for (Map.Entry<String, List<Entry>> mod : byMod().entrySet()) {
            if (mod.getValue().isEmpty()) {
                continue;
            }
            lines.add("");
            lines.add(mod.getKey().isEmpty() ? Text.raw("catalogue.file.general")
                    : modName(mod.getKey()).toUpperCase(Locale.ROOT) + " (" + mod.getKey() + ")");
            for (Entry e : mod.getValue()) {
                lines.add("  " + pad(e.id, width) + pad(e.name + " (" + e.kind.text() + ")", 48) + e.state());
            }
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BSPServerStressTest.LOGGER.error("Could not write {}", file, e);
        }
        return file;
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + "  " : s + " ".repeat(width - s.length());
    }
}
