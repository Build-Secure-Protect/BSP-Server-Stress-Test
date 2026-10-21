package com.mrgregles.bsp_server_stress_test.bots;

import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/** The bot options a run can raise (owner decision D8): standing, wandering and flying bots, all within {@code limits.maxBots}. */
public final class BotOptions {
    private BotOptions() {
    }

    /** One option per way bots behave: {@code bots.stand}, {@code bots.wander}, {@code bots.fly_out}. */
    public static final class Group implements StressOption {
        private final String id;
        private final Bots.Behaviour behaviour;
        private final String textKey;
        private final int step;

        Group(String id, Bots.Behaviour behaviour, String textKey, int step) {
            this.id = id;
            this.behaviour = behaviour;
            this.textKey = textKey;
            this.step = step;
        }

        public Bots.Behaviour behaviour() {
            return behaviour;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String describe(int level) {
            return Text.raw(textKey, Fmt.count(level));
        }

        @Override
        public int defaultStep() {
            return step;
        }

        @Override
        public int defaultCeiling() {
            return 200;
        }

        @Override
        public Limit limit() {
            return Limit.BOTS;
        }

        @Override
        public void apply(MinecraftServer server, int level) {
            Bots.setCount(server, behaviour, level);
        }

        @Override
        public void release(MinecraftServer server) {
            Bots.setCount(server, behaviour, 0);
        }
    }

    public static final String STAND = "bots.stand";
    public static final String WANDER = "bots.wander";
    public static final String FLY = "bots.fly_out";

    public static List<StressOption> all() {
        return List.of(new Group(STAND, Bots.Behaviour.STAND, "option.bots_stand", 10),
                new Group(WANDER, Bots.Behaviour.WANDER, "option.bots_wander", 10),
                new Group(FLY, Bots.Behaviour.FLY, "option.bots_fly", 5));
    }
}
