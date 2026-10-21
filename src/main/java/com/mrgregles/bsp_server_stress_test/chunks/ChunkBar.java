package com.mrgregles.bsp_server_stress_test.chunks;

import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * The live count at the top of the screen and the clickable keys in chat (owner decision D23): "Chunks held 1,200 / 4,096 · loaded on the server
 * 3,870", updated once a second for the people allowed to use {@code /stress} while the mod holds any chunk. A plain
 * vanilla boss bar, so nothing is needed in the player's game.
 */
public final class ChunkBar {
    private static ServerBossEvent bar;

    private ChunkBar() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ChunkBar::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            if (bar != null) {
                bar.removeAllPlayers();
                bar = null;
            }
            ChunkHold.forget();
        });
    }

    public static String title(int held, int max, int loaded) {
        return Text.raw("chunks.bar", Fmt.count(held), Fmt.count(max), Fmt.count(loaded));
    }

    static final int[] KEYS = {-100, -10, 10, 100};

    /** "Change it: [-100] [-10] [+10] [+100] [let go]" for chat, each key running its command when clicked (D23). */
    public static MutableComponent keys() {
        MutableComponent line = Text.t("chunks.keys");
        for (int k : KEYS) {
            line.append(" ").append(key((k > 0 ? "+" : "") + k, "/stress chunks add " + k, ChatFormatting.AQUA));
        }
        line.append(" ").append(key(Text.raw("chunks.key.release"), "/stress chunks release", ChatFormatting.GOLD));
        return line;
    }

    private static MutableComponent key(String label, String command, ChatFormatting colour) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(colour)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** The bar as it is now (null when nothing is held), for tests. */
    public static ServerBossEvent bar() {
        return bar;
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) {
            return;
        }
        update(e.getServer());
    }

    public static void update(MinecraftServer server) {
        int held = ChunkHold.total();
        if (held == 0) {
            if (bar != null) {
                bar.removeAllPlayers();
                bar = null;
            }
            return;
        }
        int max = StressConfig.MAX_CHUNKS.get();
        int loaded = 0;
        for (ServerLevel level : server.getAllLevels()) {
            loaded += level.getChunkSource().getLoadedChunksCount();
        }
        if (bar == null) {
            bar = new ServerBossEvent(Text.of(""), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bar.setName(Text.of(title(held, max, loaded)));
        bar.setProgress(max == 0 ? 0 : Math.min(1f, held / (float) max));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean should = StressCommands.canUse(p);
            boolean has = bar.getPlayers().contains(p);
            if (should && !has) {
                bar.addPlayer(p);
            } else if (!should && has) {
                bar.removePlayer(p);
            }
        }
    }
}
