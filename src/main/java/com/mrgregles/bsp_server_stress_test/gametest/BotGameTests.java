package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.bots.BotOptions;
import com.mrgregles.bsp_server_stress_test.bots.Bots;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.report.CsvReport;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.Profile;
import com.mrgregles.bsp_server_stress_test.run.RunGuard;
import com.mrgregles.bsp_server_stress_test.run.StressRun;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ST-9, bots: their names, two real logins through the player list that tick, load chunks and drain their packets, and
 * leave no file behind when they go; a run raising {@code bots.stand}; a bot the server kicks is finished off. Each in a
 * batch of its own, so no test sees another's bots.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class BotGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    /** Bots make the server spawn mobs around them, as players do; the test world must not keep them. */
    static boolean pauseSpawning(MinecraftServer server) {
        var rule = server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        boolean was = rule.get();
        rule.set(false, server);
        return was;
    }

    static void restoreSpawning(MinecraftServer server, boolean was) {
        server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(was, server);
    }

    @GameTest(template = EMPTY)
    public void bots_have_numbered_names_and_offline_uuids(GameTestHelper h) {
        if (!Bots.nameOf(1).equals("SSTBot_0001") || !Bots.nameOf(200).equals("SSTBot_0200")
                || !Bots.uuidOf("SSTBot_0001").equals(UUIDUtil.createOfflinePlayerUUID("SSTBot_0001"))) {
            h.fail("names or ids: " + Bots.nameOf(1) + " " + Bots.uuidOf("SSTBot_0001"));
            return;
        }
        if (!Catalogue.byId(BotOptions.STAND).ready()) {
            h.fail("bots.stand is not ready in the catalogue");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bots_live", timeoutTicks = 200)
    public void two_bots_log_in_as_real_players_tick_and_leave_nothing_behind(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = pauseSpawning(server);
        if (Bots.setCount(server, Bots.Behaviour.STAND, 2) != 2) {
            Bots.releaseAll(server);
            restoreSpawning(server, spawning);
            h.fail("two bots did not log in");
            return;
        }
        List<Bots.Bot> bots = Bots.online();
        for (Bots.Bot b : bots) {
            ServerPlayer p = b.player();
            if (server.getPlayerList().getPlayer(p.getUUID()) != p || !Bots.isBot(p) || !Bots.loginSeen(p.getUUID())
                    || !p.serverLevel().players().contains(p)) {
                Bots.releaseAll(server);
                restoreSpawning(server, spawning);
                h.fail(p.getGameProfile().getName() + " is not a full player: in list " + (server.getPlayerList().getPlayer(p.getUUID()) == p)
                        + ", login seen " + Bots.loginSeen(p.getUUID()));
                return;
            }
        }
        if (RunGuard.realPlayers(server.getPlayerList().getPlayers()).stream().anyMatch(Bots::isBot)) {
            Bots.releaseAll(server);
            h.fail("a bot counts as a real player");
            return;
        }
        h.runAfterDelay(20, () -> {
            try {
                if (Bots.count() != 2) {
                    h.fail("only " + Bots.count() + " bots are still online after 20 ticks");
                    return;
                }
                for (Bots.Bot b : bots) {
                    ServerPlayer p = b.player();
                    // emptied at the end of every tick: what is queued now is this tick's, already encoded to bytes
                    if (!b.channel().isOpen() || b.channel().outboundMessages().size() > 2000
                            || b.channel().outboundMessages().stream().anyMatch(m -> !(m instanceof io.netty.buffer.ByteBuf))) {
                        h.fail(p.getGameProfile().getName() + ": channel open " + b.channel().isOpen() + ", "
                                + b.channel().outboundMessages().size() + " queued, all bytes "
                                + b.channel().outboundMessages().stream().allMatch(m -> m instanceof io.netty.buffer.ByteBuf));
                        return;
                    }
                    if (!p.serverLevel().getChunkSource().hasChunk(p.chunkPosition().x, p.chunkPosition().z)) {
                        h.fail(p.getGameProfile().getName() + " stands in an unloaded chunk");
                        return;
                    }
                }
            } finally {
                Bots.releaseAll(server);
                restoreSpawning(server, spawning);
            }
            for (Bots.Bot b : bots) {
                ServerPlayer p = b.player();
                String name = p.getGameProfile().getName();
                if (server.getPlayerList().getPlayer(p.getUUID()) != null || Bots.isBot(p)) {
                    h.fail(name + " is still online");
                    return;
                }
                if (Bots.hasFiles(server, p.getUUID(), name)) {
                    h.fail(name + " left files or a name cache line behind");
                    return;
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "bots_run")
    public void a_run_raises_standing_bots_step_by_step_and_logs_them_out(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Profile p = Profile.of("bot_test", "", List.of("bots.stand"), List.of());
        p.stepSeconds = 3600;
        p.settleSeconds = 0;
        p.tuning(BotOptions.STAND).step = 1;
        p.tuning(BotOptions.STAND).ceiling = 2;
        List<String> said = new ArrayList<>();
        StressRun run = new StressRun(p, List.of(Options.byId(BotOptions.STAND)), said::add, 20_000_000_000L, 10_000_000_000L);
        long now = System.nanoTime();
        boolean spawning = pauseSpawning(server);
        try {
            run.begin(server, now);
            run.up(server, now);
            if (Bots.count() != 1) {
                h.fail("step 1: " + Bots.count() + " bots");
                return;
            }
            run.up(server, now);
            if (Bots.count() != 2 || said.stream().noneMatch(l -> l.equals("Step 2: 2 bots standing, spread out at the places"))) {
                h.fail("step 2: " + Bots.count() + " bots; said " + said);
                return;
            }
        } finally {
            run.stop(server, "the test");
            restoreSpawning(server, spawning);
            for (Path f : new Path[]{run.csv(), run.csv() == null ? null : CsvReport.timingsOf(run.csv()), run.report()}) {
                try {
                    if (f != null) {
                        Files.deleteIfExists(f);
                    }
                } catch (Exception ignored) {
                    // a test file left in run/ harms nothing
                }
            }
        }
        if (Bots.count() != 0) {
            h.fail("the run's end left " + Bots.count() + " bots online");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bots_kick", timeoutTicks = 100)
    public void a_bot_the_server_kicks_is_logged_out_properly(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = pauseSpawning(server);
        Bots.setCount(server, Bots.Behaviour.STAND, 1);
        if (Bots.count() != 1) {
            restoreSpawning(server, spawning);
            h.fail("the bot did not log in");
            return;
        }
        ServerPlayer p = Bots.online().get(0).player();
        p.connection.disconnect(Component.literal("kicked by a test"));
        h.runAfterDelay(3, () -> {
            boolean gone = server.getPlayerList().getPlayer(p.getUUID()) == null && !Bots.isBot(p) && Bots.count() == 0;
            boolean clean = !Bots.hasFiles(server, p.getUUID(), p.getGameProfile().getName());
            Bots.releaseAll(server);
            restoreSpawning(server, spawning);
            if (!gone || !clean) {
                h.fail("after the kick: gone " + gone + ", files deleted " + clean);
                return;
            }
            h.succeed();
        });
    }
}
