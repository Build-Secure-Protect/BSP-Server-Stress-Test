package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.compat.BspCore;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ST-0: the wiring, and the three rules every later task builds on: every file lives in the mod's own folder (D4), every
 * message reaches a player's game as plain English (the mod is server only, D19, D21), and {@code /stress} is for
 * operators at level 4 and BSP-Core admins (D3).
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class SetupGameTests {
    static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public void mod_is_loaded_beside_bsp_core_and_a_mock_player_can_be_made(GameTestHelper h) {
        if (!ModList.get().isLoaded(BSPServerStressTest.MODID)) {
            h.fail("bsp_server_stress_test is not loaded");
            return;
        }
        if (!ModList.get().isLoaded("bsp_core")) {
            h.fail("bsp_core is not loaded in the dev run");
            return;
        }
        ServerPlayer player = TestPlayers.mockServerPlayer(h);
        if (player.getGameProfile().getName().isEmpty()) {
            h.fail("mock player has no name");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void every_file_lives_in_the_mods_own_config_folder(GameTestHelper h) {
        Path root = StressFiles.root();
        if (!root.equals(FMLPaths.CONFIGDIR.get().resolve(BSPServerStressTest.MODID).toAbsolutePath().normalize())) {
            h.fail("the folder is " + root + ", not config/bsp_server_stress_test");
            return;
        }
        Path settings = FMLPaths.CONFIGDIR.get().resolve(StressConfig.FILE_NAME).toAbsolutePath().normalize();
        if (!Files.isRegularFile(settings) || !settings.startsWith(root)) {
            h.fail("the settings file is not " + root.resolve("settings.toml") + " (looked for " + settings + ")");
            return;
        }
        Path world = h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (root.startsWith(world) || world.startsWith(root)) {
            h.fail("the mod's folder and the world folder overlap: " + root + " and " + world);
            return;
        }
        if (!StressFiles.file("reports/run.txt").equals(root.resolve("reports/run.txt"))) {
            h.fail("a report name did not resolve inside the folder");
            return;
        }
        for (String escape : new String[]{"../escape.txt", "reports/../../escape.txt", world.resolve("level.dat").toString(), "."}) {
            try {
                StressFiles.file(escape);
                h.fail("a file outside the folder was allowed: " + escape);
                return;
            } catch (IllegalArgumentException expected) {
                // refused, as it should be
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void messages_reach_a_game_without_the_mod_as_plain_english(GameTestHelper h) {
        MutableComponent line = Text.t("status.title", "9.9.9");
        if (!(line.getContents() instanceof LiteralContents)) {
            h.fail("the message is not plain text: " + line.getContents().getClass().getSimpleName());
            return;
        }
        if (!line.getString().equals("BSP-Server Stress Test 9.9.9")) {
            h.fail("the message read \"" + line.getString() + "\"");
            return;
        }
        if (!Text.raw("no.such.key").equals(Text.PREFIX + "no.such.key")) {
            h.fail("a missing key did not come back as the key");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void stress_is_for_level_4_operators_and_bsp_core_admins_only(GameTestHelper h) {
        ServerPlayer player = TestPlayers.mockServerPlayer(h);
        var ops = h.getLevel().getServer().getPlayerList().getOps();
        try {
            if (StressCommands.canUse(player)) {
                h.fail("a player who is not an operator may use /stress");
                return;
            }
            // Below level 4 only BSP-Core's word counts. With its admin list empty (as here) BSP-Core counts operators of
            // level 2 and up as admins, so a level 2 operator passes through BSP-Core and not through the level.
            ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
            if (StressCommands.canUse(player) != BspCore.isAdmin(player)) {
                h.fail("a level 2 operator's access does not follow BSP-Core's admin check");
                return;
            }
            if (!BspCore.isAdmin(player)) {
                h.fail("BSP-Core with an empty admin list did not count a level 2 operator as an admin");
                return;
            }
            ops.add(new ServerOpListEntry(player.getGameProfile(), 4, false));
            if (!StressCommands.canUse(player)) {
                h.fail("a level 4 operator may not use /stress");
                return;
            }
        } finally {
            ops.remove(player.getGameProfile());
        }
        h.succeed();
    }
}
