package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.run.Profile;
import com.mrgregles.bsp_server_stress_test.run.Profiles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;

/**
 * ST-3, the options catalogue: built from what the server loaded (here Minecraft, Forge and BSP-Core): ticking blocks
 * are machines and quiet ones are not, technical and listed things are left out with the reason, mobs and explosives
 * are found, names are English from the mods' own files, profiles cover the right entries, and the file is written.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class CatalogueGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    private static Catalogue.Entry need(GameTestHelper h, String id) {
        Catalogue.Entry e = Catalogue.byId(id);
        if (e == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("no catalogue entry " + id);
        }
        return e;
    }

    @GameTest(template = EMPTY)
    public void blocks_that_tick_are_machines_and_quiet_ones_are_not(GameTestHelper h) {
        for (String id : List.of("machines.minecraft.hopper", "machines.minecraft.furnace", "machines.minecraft.beacon")) {
            Catalogue.Entry e = need(h, id);
            if (e.kind() != Catalogue.Kind.MACHINE || e.leftOut() || !e.note().equals("ST-11")) {
                h.fail(id + ": " + e);
                return;
            }
        }
        // a chest's block entity only ticks in the player's game (the lid), a barrel's never
        for (String quiet : List.of("machines.minecraft.chest", "machines.minecraft.barrel", "machines.minecraft.stone")) {
            if (Catalogue.byId(quiet) != null) {
                h.fail("a block that does not tick on the server is a machine: " + quiet);
                return;
            }
        }
        if (Catalogue.byId("machines.minecraft.oak_wall_sign") != null || Catalogue.byId("machines.minecraft.oak_sign") == null) {
            h.fail("a wall sign is listed apart from the sign, or the sign is missing");
            return;
        }
        if (Catalogue.byId("machines.minecraft.tnt") != null || need(h, "explosions.minecraft.tnt").kind() != Catalogue.Kind.EXPLOSIVE) {
            h.fail("TNT is not an explosive only");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void technical_and_listed_things_are_left_out_with_the_reason(GameTestHelper h) {
        Catalogue.Entry piston = need(h, "machines.minecraft.moving_piston");
        if (!piston.leftOut() || !piston.state().contains("no item")) {
            h.fail("moving piston: " + piston.state());
            return;
        }
        Catalogue.Entry wither = need(h, "entities.minecraft.wither");
        if (!wither.leftOut() || !wither.state().contains("leaveOut")) {
            h.fail("wither: " + wither.state());
            return;
        }
        if (!Catalogue.leftOutByList(ResourceLocation.tryBuild("bsp_core", "shatter_totem"))
                || Catalogue.leftOutByList(ResourceLocation.tryBuild("bsp_core", "tetrium_crucible"))) {
            h.fail("the leave-out list did not hold the totem only");
            return;
        }
        for (Catalogue.Entry e : Catalogue.entries()) {
            if (e.id().endsWith(".bsp_core.shatter_totem") && !e.leftOut()) {
                h.fail("BSP-Core's totem is offered: " + e.id());
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void mobs_are_the_summonable_living_kinds(GameTestHelper h) {
        Catalogue.Entry zombie = need(h, "entities.minecraft.zombie");
        if (zombie.kind() != Catalogue.Kind.MOB || zombie.leftOut() || !zombie.name().equals("Zombie")) {
            h.fail("zombie: " + zombie);
            return;
        }
        for (String misc : List.of("entities.minecraft.item", "entities.minecraft.arrow", "entities.minecraft.player")) {
            if (Catalogue.byId(misc) != null) {
                h.fail("not a mob, yet listed: " + misc);
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void explosives_are_guessed_by_how_their_names_end(GameTestHelper h) {
        String[][] yes = {{"tnt", ""}, {"deepslate_bomb", ""}, {"nuke", ""}, {"thing", "NukeBlock"},
                {"condensive", "BlockExplosive"}, {"thermonuclearbomb", "ThermonuclearbombBlock"}};
        String[][] no = {{"explosive_crafter", "ExplosiveCrafterBlock"}, {"nuclear_reactor_core", "ReactorBlock"},
                {"battery_charger", "ChargerBlock"}, {"bomb_shelter_door", "DoorBlock"}};
        for (String[] c : yes) {
            if (!Catalogue.looksExplosive(c[0], c[1].isEmpty() ? List.of() : List.of(c[1]))) {
                h.fail("not taken for an explosive: " + c[0] + " " + c[1]);
                return;
            }
        }
        for (String[] c : no) {
            if (Catalogue.looksExplosive(c[0], List.of(c[1]))) {
                h.fail("taken for an explosive: " + c[0] + " " + c[1]);
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void names_are_english_from_the_mods_own_files(GameTestHelper h) {
        if (!need(h, "machines.minecraft.hopper").name().equals("Hopper")) {
            h.fail("hopper is called " + need(h, "machines.minecraft.hopper").name());
            return;
        }
        long bspCore = 0;
        for (Catalogue.Entry e : Catalogue.entries()) {
            if ((e.modId().equals("minecraft") || e.modId().equals("bsp_core"))
                    && (e.name().startsWith("block.") || e.name().startsWith("entity.") || e.name().contains(":"))) {
                h.fail("no English name for " + e.id() + ": " + e.name());
                return;
            }
            if (e.modId().equals("bsp_core") && e.kind() == Catalogue.Kind.MACHINE) {
                bspCore++;
            }
        }
        if (bspCore == 0) {
            h.fail("no BSP-Core machines found");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void general_options_and_profiles_cover_the_right_entries(GameTestHelper h) {
        Catalogue.Entry spin = need(h, "test.spin");
        Catalogue.Entry hoppers = need(h, "world.hoppers");
        Catalogue.Entry planned = new Catalogue.Entry("world.not_built", Catalogue.Kind.GENERAL, "", "x", false, "ST-99");
        if (!spin.ready() || !hoppers.ready() || planned.ready() || !planned.state().equals("comes with ST-99")) {
            h.fail("test.spin " + spin.state() + "; world.hoppers " + hoppers.state() + "; planned " + planned.state());
            return;
        }
        for (Catalogue.Entry e : Catalogue.entries()) {
            if (e.kind() == Catalogue.Kind.GENERAL && !e.ready()) {
                h.fail(e.id() + " is a general option that is not ready: " + e.state());
                return;
            }
        }
        try {
            Profile pre = Profiles.load("pre_generated");
            Profile machines = Profiles.load("machines_only");
            List<String> preIds = Catalogue.chosenBy(pre).stream().map(Catalogue.Entry::id).toList();
            if (preIds.contains("chunks.generate_fresh") || preIds.contains("bots.fly_out") || preIds.contains("test.spin")
                    || !preIds.contains("chunks.hold") || !preIds.contains("machines.minecraft.hopper")) {
                h.fail("pre_generated covers the wrong things");
                return;
            }
            if (Catalogue.chosenBy(machines).stream().anyMatch(e -> e.kind() != Catalogue.Kind.MACHINE)
                    || Catalogue.chosenBy(machines).stream().anyMatch(Catalogue.Entry::leftOut)) {
                h.fail("machines_only covers something that is not an offered machine");
                return;
            }
        } catch (Exception e) {
            h.fail("profiles: " + e);
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_list_is_written_to_options_txt_in_the_mods_folder(GameTestHelper h) {
        try {
            var file = Catalogue.write();
            if (!file.equals(StressFiles.file(Catalogue.FILE))) {
                h.fail("written to " + file);
                return;
            }
            String text = Files.readString(file);
            if (!text.contains("machines.minecraft.hopper") || !text.contains("Hopper (ticking block)")
                    || !text.contains("left out: no item") || !text.contains("GENERAL (every server)")
                    || !text.contains("world.random_ticks") || !text.contains("Hopper (ticking block)                          ready")) {
                h.fail("options.txt is missing expected lines");
                return;
            }
        } catch (Exception e) {
            h.fail("options.txt: " + e);
            return;
        }
        h.succeed();
    }
}
