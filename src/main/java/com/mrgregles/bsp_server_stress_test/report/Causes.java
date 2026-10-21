package com.mrgregles.bsp_server_stress_test.report;

import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.text.Text;
import com.mrgregles.bsp_server_stress_test.timing.TimingReport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The "why" in a report (owner decision D17): one plain sentence for each kind of cost, kept in the language file
 * ({@code cause.*}, written by {@code tools/gen_lang.py}). A block or entity gets its own sentence when the file has one
 * for its id ({@code cause.block.minecraft.hopper}), otherwise the sentence for its kind (a ticking block, a mob, a
 * dropped item, another entity). The sentence explains; the numbers beside it are always measured.
 */
public final class Causes {
    /** Mods that take part in making new land, named when a run generated chunks. */
    static final List<String> WORLDGEN_MODS = List.of("terralith", "biomesoplenty", "terrablender", "continents",
            "amplified_nether", "bygonenether", "structory", "structory_towers", "t_and_t", "dungeoncrawl", "yungsapi", "noisium");

    private Causes() {
    }

    public static String forThing(TimingReport.Thing t) {
        String specific = (t.blockEntity() ? "block." : "entity.") + t.id().getNamespace() + "." + t.id().getPath();
        if (Text.has("cause." + specific)) {
            return Text.raw("cause." + specific);
        }
        if (t.blockEntity()) {
            return Text.raw("cause.kind.block", Catalogue.modName(t.modId()));
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(t.id()).orElse(null);
        if (type != null && type.getCategory() != MobCategory.MISC) {
            return Text.raw("cause.kind.mob");
        }
        return Text.raw("cause.kind.entity", Catalogue.modName(t.modId()));
    }

    /** The world generation mods this server loaded, by name ("Terralith, Biomes O' Plenty"), or "" when none. */
    public static String worldgenMods() {
        if (ModList.get() == null) {
            return "";
        }
        return WORLDGEN_MODS.stream().filter(id -> ModList.get().isLoaded(id)).map(Catalogue::modName)
                .collect(Collectors.joining(", "));
    }
}
