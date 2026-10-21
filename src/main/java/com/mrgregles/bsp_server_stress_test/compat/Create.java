package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.entities.Spawns;
import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.machines.Placements.Part;
import com.mrgregles.bsp_server_stress_test.machines.Placements.Shape;
import com.mrgregles.bsp_server_stress_test.setups.Feed;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class that touches Create (owner decisions D35, D36, D41): its working setups, kinetic lines turned by a
 * creative motor (ST-15) and moving contraptions (ST-16, D42 to D45). Create is never compiled against: its blocks are found by id and their states set by
 * property name, items go in and out through Forge's item hookup ({@link Feed}), and the two things only Create's own
 * code can do (a motor's speed, laying a belt) are reflective calls in the nested {@link Calls}. A Create that has
 * renamed one of these blocks offers no setups; one that has moved a method leaves that setup unturned or beltless,
 * which the log says.
 */
public final class Create {
    public static final String MODID = "create";
    /** The speed every setup's creative motor is set to (Create's default is 16; 64 gives a fan 7 blocks of reach). */
    public static final int MOTOR_RPM = 64;

    public static final String PRESS_LINE = "setups.create.press_line";
    public static final String MILLSTONES = "setups.create.millstones";
    public static final String FAN_SMOKING = "setups.create.fan_smoking";
    public static final String BELT_LINE = "setups.create.belt_line";
    public static final String BEARING_ROTOR = "setups.create.bearing_rotor";
    public static final String PISTON_LINE = "setups.create.piston_line";
    public static final String TRAIN_LINE = "setups.create.train_line";
    /** Every train a train line assembles is named starting with this, so cleanup can find one a crash left. */
    public static final String TRAIN_PREFIX = "SST ";

    private Create() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    /**
     * Gives every moving contraption that appears in a moving setup's space the mod's tag the moment it joins the world
     * (D45), so one a stopped server leaves is refused when its chunk loads.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() || !isContraption(e.getEntity())) {
                return;
            }
            for (Placements.Unit u : Placements.allUnits()) {
                if (u.shape() instanceof Moving && u.dimension().equals(e.getLevel().dimension())
                        && u.box().contains(e.getEntity().position())) {
                    Spawns.adopt(e.getEntity());
                    return;
                }
            }
        });
    }

    /** Whether the entity is one of Create's moving contraptions (a bearing's, a piston's, a cart's). */
    public static boolean isContraption(Entity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id.getNamespace().equals(MODID) && id.getPath().contains("contraption");
    }

    static Block block(String path) {
        return ModBlocks.block(MODID, path);
    }

    static BlockState with(BlockState state, String name, String value) {
        return ModBlocks.with(state, name, value);
    }

    /** The setups, or none when a block they need is missing (a Create too old or too new). */
    public static List<SetupOption> setups() {
        Block motor = block("creative_motor");
        Block shaft = block("shaft");
        Block press = block("mechanical_press");
        Block depot = block("depot");
        Block millstone = block("millstone");
        Block fan = block("encased_fan");
        Block belt = block("belt");
        Block bearing = block("mechanical_bearing");
        Block piston = block("sticky_mechanical_piston");
        Block pole = block("piston_extension_pole");
        Block head = block("mechanical_piston_head");
        Block gearshift = block("gearshift");
        Block track = block("track");
        Block station = block("track_station");
        Block bogey = block("small_bogey");
        Block controls = block("controls");
        Block seat = block("white_seat");
        Item schedule = ModBlocks.item(MODID, "schedule");
        if (track == null || station == null || bogey == null || controls == null || seat == null || schedule == null
                || motor == null || shaft == null || press == null || depot == null || millstone == null || fan == null || belt == null
                || bearing == null || piston == null || pole == null || head == null || gearshift == null) {
            BSPServerStressTest.LOGGER.warn("Create is loaded but a block its setups need is missing; no Create setups are offered");
            return List.of();
        }
        List<SetupOption> out = new ArrayList<>();
        out.add(new SetupOption(PRESS_LINE, MODID, "setup.create.press_line", pressLine(motor, press, depot)));
        out.add(new SetupOption(MILLSTONES, MODID, "setup.create.millstones", millstones(motor, shaft, millstone)));
        out.add(new SetupOption(FAN_SMOKING, MODID, "setup.create.fan_smoking", fanSmoking(motor, fan, depot)));
        out.add(new SetupOption(BELT_LINE, MODID, "setup.create.belt_line", beltLine(motor, shaft, belt)));
        out.add(new SetupOption(BEARING_ROTOR, MODID, "setup.create.bearing_rotor",
                bearingRotor(motor, bearing, StressConfig.SETUPS_ROTOR_SIDE.get())));
        out.add(new SetupOption(PISTON_LINE, MODID, "setup.create.piston_line",
                pistonLine(motor, gearshift, piston, pole, head, StressConfig.SETUPS_PISTON_SIDE.get())));
        out.add(new SetupOption(TRAIN_LINE, MODID, "setup.create.train_line", trainLine(track, station, bogey, controls, seat, schedule)));
        return out;
    }

    /**
     * A shape whose work is feeding {@code input} to the parts at {@code fed} every {@link Feed#EVERY} ticks, with its
     * motors set to {@code rpm}.
     */
    private abstract static class Fed implements Shape {
        final List<Part> parts;
        final List<BlockPos> motors;
        final List<BlockPos> fed;
        final Item input;
        final int rpm;

        Fed(List<Part> parts, List<BlockPos> motors, List<BlockPos> fed, Item input, int rpm) {
            this.parts = List.copyOf(parts);
            this.motors = List.copyOf(motors);
            this.fed = List.copyOf(fed);
            this.input = input;
            this.rpm = rpm;
        }

        Fed(List<Part> parts, List<BlockPos> motors, List<BlockPos> fed, Item input) {
            this(parts, motors, fed, input, MOTOR_RPM);
        }

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public void placed(ServerLevel level, BlockPos origin) {
            for (BlockPos m : motors) {
                Calls.motorSpeed(level.getBlockEntity(origin.offset(m)), rpm);
            }
        }

        @Override
        public void removing(ServerLevel level, BlockPos origin) {
            // a millstone lets nothing take its input out from outside, and drops it when it goes
            for (BlockPos p : fed) {
                Feed.clearStores(level.getBlockEntity(origin.offset(p)));
            }
        }

        @Override
        public int every() {
            return Feed.EVERY;
        }

        @Override
        public void cycle(ServerLevel level, BlockPos origin) {
            for (BlockPos p : fed) {
                Feed.work(level, origin.offset(p), input, 16);
            }
        }

        /** The block positions an input is fed at, for tests. */
        public List<BlockPos> fed() {
            return fed;
        }
    }

    private static Part part(int x, int y, int z, BlockState state) {
        return Part.of(x, y, z, state);
    }

    /** A creative motor turning three mechanical presses in a row, each over a depot of iron ingots (pressed into sheets). */
    static Shape pressLine(Block motor, Block press, Block depot) {
        List<Part> parts = new ArrayList<>();
        parts.add(part(0, 2, 0, with(motor.defaultBlockState(), "facing", "east")));
        List<BlockPos> fed = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            parts.add(part(x, 2, 0, with(press.defaultBlockState(), "facing", "east")));
            parts.add(part(x, 0, 0, depot.defaultBlockState()));
            fed.add(new BlockPos(x, 0, 0));
        }
        return new Fed(parts, List.of(BlockPos.ZERO.above(2)), fed, Items.IRON_INGOT) {
        };
    }

    /** Three millstones, each turned through a shaft by a creative motor below it, grinding cobblestone into gravel. */
    static Shape millstones(Block motor, Block shaft, Block millstone) {
        List<Part> parts = new ArrayList<>();
        List<BlockPos> motors = new ArrayList<>();
        List<BlockPos> fed = new ArrayList<>();
        for (int x = 0; x <= 4; x += 2) {
            parts.add(part(x, 0, 0, with(motor.defaultBlockState(), "facing", "up")));
            parts.add(part(x, 1, 0, with(shaft.defaultBlockState(), "axis", "y")));
            parts.add(part(x, 2, 0, millstone.defaultBlockState()));
            motors.add(new BlockPos(x, 0, 0));
            fed.add(new BlockPos(x, 2, 0));
        }
        return new Fed(parts, motors, fed, Items.COBBLESTONE) {
        };
    }

    /**
     * A creative motor driving an encased fan that blows through a lit campfire over three depots of raw beef
     * (Create's bulk smoking: the beef is cooked by the air; the depots sit one block below the air so they do not
     * block it).
     */
    static Shape fanSmoking(Block motor, Block fan, Block depot) {
        List<Part> parts = new ArrayList<>();
        parts.add(part(0, 1, 0, with(motor.defaultBlockState(), "facing", "east")));
        parts.add(part(1, 1, 0, with(fan.defaultBlockState(), "facing", "east")));
        parts.add(part(2, 1, 0, Blocks.CAMPFIRE.defaultBlockState()));
        List<BlockPos> fed = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            parts.add(part(x, 0, 0, depot.defaultBlockState()));
            fed.add(new BlockPos(x, 0, 0));
        }
        return new Fed(parts, List.of(BlockPos.ZERO.above()), fed, Items.BEEF) {
        };
    }

    /** Where the belt line's belt starts and ends (its two pulleys). */
    public static final BlockPos BELT_START = new BlockPos(1, 1, 1);
    public static final BlockPos BELT_END = new BlockPos(8, 1, 1);

    /**
     * A creative motor turning an 8-block belt that carries cobblestone from one end to the other, with a stone block
     * at each end so nothing falls off; cobblestone goes on at the start and is taken off at the end. The motor turns
     * the other way round (a belt laid from west to east and turned from the north carries westward at a positive
     * speed).
     */
    static Shape beltLine(Block motor, Block shaft, Block belt) {
        List<Part> parts = new ArrayList<>();
        parts.add(part(1, 1, 0, with(motor.defaultBlockState(), "facing", "south")));
        parts.add(part(0, 1, 1, Blocks.STONE.defaultBlockState()));
        parts.add(part(9, 1, 1, Blocks.STONE.defaultBlockState()));
        BlockState pulley = with(shaft.defaultBlockState(), "axis", "z");
        parts.add(new Part(BELT_START, pulley, List.of(shaft, belt)));
        parts.add(new Part(BELT_END, pulley, List.of(shaft, belt)));
        for (int x = BELT_START.getX() + 1; x < BELT_END.getX(); x++) {
            parts.add(new Part(new BlockPos(x, 1, 1), null, List.of(belt))); // laid by Create when the belt is made
        }
        return new Fed(parts, List.of(new BlockPos(1, 1, 0)), List.of(BELT_START), Items.COBBLESTONE, -MOTOR_RPM) {
            @Override
            public void placed(ServerLevel level, BlockPos origin) {
                Calls.createBelt(level, origin.offset(BELT_START), origin.offset(BELT_END));
                super.placed(level, origin);
            }

            @Override
            public void cycle(ServerLevel level, BlockPos origin) {
                Feed.drain(level, origin.offset(BELT_END));
                super.cycle(level, origin);
            }

            @Override
            public void removing(ServerLevel level, BlockPos origin) {
                super.removing(level, origin);
                // whatever is on the belt is taken off first: a belt that goes drops what it carries
                if (!Calls.clearBelt(level.getBlockEntity(origin.offset(BELT_START)))) {
                    for (int x = BELT_START.getX(); x <= BELT_END.getX(); x++) {
                        Feed.drain(level, origin.offset(x, 1, 1));
                    }
                }
            }
        };
    }

    /**
     * A setup with a moving contraption (D43, D45): its motors are set going when placed, its {@code anchor} (the bearing
     * or piston) builds the contraption, and before it is removed the anchor forgets the contraption and the moving
     * piece is discarded, so Create puts no blocks down anywhere.
     */
    abstract static class Moving implements Shape {
        final List<Part> parts;
        final BlockPos motor;
        final BlockPos anchor;

        Moving(List<Part> parts, BlockPos motor, BlockPos anchor) {
            this.parts = List.copyOf(parts);
            this.motor = motor;
            this.anchor = anchor;
        }

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public void placed(ServerLevel level, BlockPos origin) {
            Calls.motorSpeed(level.getBlockEntity(origin.offset(motor)), MOTOR_RPM);
        }

        @Override
        public void removing(ServerLevel level, BlockPos origin) {
            Calls.forgetContraption(level.getBlockEntity(origin.offset(anchor)));
            AABB space = new AABB(origin).inflate(SETUP_REACH);
            level.getEntitiesOfClass(Entity.class, space, Create::isContraption).forEach(Entity::discard);
        }
    }

    /** How far from a moving setup's corner its moving piece can be. */
    static final int SETUP_REACH = 16;

    /**
     * A creative motor turning a mechanical bearing, facing up, with a plate of {@code side} x {@code side} slime blocks
     * on it: the bearing builds the plate into a contraption as soon as it turns and spins it forever. Slime, not planks:
     * Create moves the blocks a sticky block touches, so the plate holds together without super glue (which would be
     * an entity of its own, left behind after a crash).
     */
    static Shape bearingRotor(Block motor, Block bearing, int side) {
        List<Part> parts = new ArrayList<>();
        parts.add(part(4, 0, 4, with(motor.defaultBlockState(), "facing", "up")));
        parts.add(part(4, 1, 4, with(bearing.defaultBlockState(), "facing", "up")));
        int from = 4 - side / 2;
        for (int x = from; x < from + side; x++) {
            for (int z = from; z < from + side; z++) {
                parts.add(part(x, 2, z, Blocks.SLIME_BLOCK.defaultBlockState()));
            }
        }
        return new Moving(parts, new BlockPos(4, 0, 4), new BlockPos(4, 1, 4)) {
        };
    }

    /** Ticks between two flips of a piston line's gearshift (D43: 4 seconds). */
    public static final int PISTON_EVERY = 80;
    /** Extension poles behind a piston line's piston: how far it pushes (D43: 4 blocks). */
    public static final int PISTON_POLES = 4;
    /** Where a piston line's piston and the redstone spot that flips its gearshift are. */
    public static final BlockPos PISTON = new BlockPos(5, 2, 4);
    public static final BlockPos PISTON_SWITCH = new BlockPos(5, 1, 5);

    /**
     * A creative motor below a gearshift below a sticky mechanical piston facing east (a plain one pushes but does not
     * pull back), with four extension poles behind it and a {@code side} x {@code side} face of slime blocks in front. Every 4 seconds a redstone block beside the gearshift
     * comes or goes, which turns the piston the other way: it builds the face (and its poles) into a contraption, moves
     * it 4 blocks, and puts the blocks down at the end, out and back. The whole way the face and poles travel is kept
     * clear and recorded, wherever the blocks were put down.
     */
    static Shape pistonLine(Block motor, Block gearshift, Block piston, Block pole, Block head, int side) {
        List<Part> parts = new ArrayList<>();
        parts.add(part(5, 0, 4, with(motor.defaultBlockState(), "facing", "up")));
        parts.add(part(5, 1, 4, with(gearshift.defaultBlockState(), "axis", "y")));
        parts.add(part(PISTON.getX(), PISTON.getY(), PISTON.getZ(),
                with(with(piston.defaultBlockState(), "facing", "east"), "axis_along_first", "true")));
        List<Block> travelling = List.of(Blocks.SLIME_BLOCK, pole, head);
        for (int x = PISTON.getX() - PISTON_POLES; x < PISTON.getX(); x++) {
            parts.add(new Part(new BlockPos(x, PISTON.getY(), PISTON.getZ()), with(pole.defaultBlockState(), "facing", "east"), List.of(pole, head)));
        }
        int fromZ = PISTON.getZ() - side / 2;
        for (int x = PISTON.getX() + 1; x <= PISTON.getX() + 1 + PISTON_POLES; x++) {
            for (int y = PISTON.getY(); y < PISTON.getY() + side; y++) {
                for (int z = fromZ; z < fromZ + side; z++) {
                    boolean face = x == PISTON.getX() + 1;
                    parts.add(new Part(new BlockPos(x, y, z), face ? Blocks.SLIME_BLOCK.defaultBlockState() : null, travelling));
                }
            }
        }
        parts.add(new Part(PISTON_SWITCH, null, List.of(Blocks.REDSTONE_BLOCK)));
        return new Moving(parts, new BlockPos(5, 0, 4), PISTON) {
            @Override
            public int every() {
                return PISTON_EVERY;
            }

            @Override
            public void cycle(ServerLevel level, BlockPos origin) {
                BlockPos at = origin.offset(PISTON_SWITCH);
                level.setBlock(at, level.getBlockState(at).is(Blocks.REDSTONE_BLOCK) ? Blocks.AIR.defaultBlockState()
                        : Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
        };
    }

    /** Where a train line's two stations stand, the track spots they target, and how long the track is. */
    public static final BlockPos STATION_A = new BlockPos(3, 1, 0);
    public static final BlockPos STATION_B = new BlockPos(12, 1, 0);
    public static final BlockPos STOP_A = new BlockPos(3, 1, 1);
    public static final BlockPos STOP_B = new BlockPos(12, 1, 1);
    static final int TRACK_FROM = 1;
    static final int TRACK_TO = 14;
    /** Seconds a train waits at each station (ST-17). */
    static final int TRAIN_WAIT = 3;

    /**
     * ST-17: a 14-block straight track with a station near each end, and one small train shuttling between them on a
     * schedule, waiting 3 seconds at each. The train is a small bogey standing on the track at station A, three slime
     * blocks on it holding a seat with a controls block on each side, facing both ways, so it drives both ways; on the
     * seat sits its conductor (Create runs a schedule only with a mob seated behind the controls), a pig with its AI
     * off, so it costs next to nothing, carrying the mod's tag. The train is assembled at station A by Create's own
     * assembly (the station's assembly mode, as a player's clicks), with a schedule item in the station that Create gives
     * every train assembled there. The stations are named after where the setup is, and the train after them.
     */
    static Shape trainLine(Block track, Block station, Block bogey, Block controls, Block seat, Item scheduleItem) {
        List<Part> parts = new ArrayList<>();
        BlockState straight = with(track.defaultBlockState(), "shape", "xo");
        for (int x = TRACK_FROM; x <= TRACK_TO; x++) {
            parts.add(new Part(new BlockPos(x, 1, 1), straight, List.of(track)));
        }
        parts.add(part(STATION_A.getX(), STATION_A.getY(), STATION_A.getZ(), station.defaultBlockState()));
        parts.add(part(STATION_B.getX(), STATION_B.getY(), STATION_B.getZ(), station.defaultBlockState()));
        for (int x = TRACK_FROM; x <= TRACK_TO; x++) { // where the train runs: its bogey on the track, the rest above
            parts.add(new Part(new BlockPos(x, 2, 1), null, List.of(bogey)));
            parts.add(new Part(new BlockPos(x, 3, 1), null, List.of(Blocks.SLIME_BLOCK)));
            parts.add(new Part(new BlockPos(x, 4, 1), null, List.of(controls, seat)));
        }
        return new Moving(parts, STATION_A, STATION_A) {
            @Override
            public void placed(ServerLevel level, BlockPos origin) {
                Calls.target(level.getBlockEntity(origin.offset(STATION_A)), STOP_A.subtract(STATION_A), true);
                Calls.target(level.getBlockEntity(origin.offset(STATION_B)), STOP_B.subtract(STATION_B), false);
            }

            @Override
            public int every() {
                return 20;
            }

            @Override
            public void cycle(ServerLevel level, BlockPos origin) {
                String name = TRAIN_PREFIX + origin.toShortString();
                if (Calls.trainNamed(name) != null) {
                    return;
                }
                BlockEntity a = level.getBlockEntity(origin.offset(STATION_A));
                BlockEntity b = level.getBlockEntity(origin.offset(STATION_B));
                if (!Calls.hasStation(a) || !Calls.hasStation(b)) {
                    return; // the track network and the stations on it are made over the first ticks
                }
                Calls.nameStation(a, name + " A");
                Calls.nameStation(b, name + " B");
                if (!Calls.assembling(a)) {
                    Calls.enterAssembly(a); // as a player does before putting the bogey on the track
                    return;
                }
                // a bogey stands on the track (as a player's casing puts it), one step from the marker into the assembly area
                BlockPos at = origin.offset(STOP_A).east().above();
                if (!level.getBlockState(at).is(bogey)) {
                    level.setBlock(at, with(bogey.defaultBlockState(), "axis", "x"), Block.UPDATE_ALL);
                    for (int dx = -1; dx <= 1; dx++) {
                        level.setBlock(at.above().east(dx), Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    BlockPos top = at.above(2);
                    // a controls block faces its driver: each faces the seat between them
                    level.setBlock(top.west(), with(controls.defaultBlockState(), "facing", "east"), Block.UPDATE_ALL);
                    level.setBlock(top, seat.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(top.east(), with(controls.defaultBlockState(), "facing", "west"), Block.UPDATE_ALL);
                    net.minecraft.world.entity.animal.Pig pig = net.minecraft.world.entity.EntityType.PIG.create(level);
                    if (pig != null) {
                        pig.setNoAi(true);
                        pig.setSilent(true);
                        pig.setInvulnerable(true);
                        pig.setPersistenceRequired();
                        pig.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0, 0);
                        Spawns.adopt(pig);
                        level.addFreshEntity(pig);
                        Calls.sitDown(level, top, pig);
                    }
                }
                if (Feed.count(level, origin.offset(STATION_A), scheduleItem) == 0) {
                    ItemStack item = new ItemStack(scheduleItem);
                    item.getOrCreateTag().put("Schedule", shuttle(name + " B", name + " A"));
                    for (net.minecraftforge.items.IItemHandler h : Feed.handlers(a)) {
                        if (net.minecraftforge.items.ItemHandlerHelper.insertItem(h, item, false).isEmpty()) {
                            break;
                        }
                    }
                }
                Calls.assemble(a, name);
            }

            @Override
            public void removing(ServerLevel level, BlockPos origin) {
                Calls.removeTrain(Calls.trainNamed(TRAIN_PREFIX + origin.toShortString()));
                super.removing(level, origin);
                // the conductor, wherever it sits or was left
                level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, new AABB(origin).inflate(SETUP_REACH),
                        e -> e.getTags().contains(Spawns.TAG)).forEach(Entity::discard);
            }
        };
    }

    /** A schedule going to {@code first}, waiting, then to {@code second}, waiting, and round again, in Create's saved form. */
    static net.minecraft.nbt.CompoundTag shuttle(String first, String second) {
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        for (String stop : List.of(first, second)) {
            net.minecraft.nbt.CompoundTag instruction = new net.minecraft.nbt.CompoundTag();
            instruction.putString("Id", "create:destination");
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.putString("Text", stop);
            instruction.put("Data", data);
            net.minecraft.nbt.CompoundTag wait = new net.minecraft.nbt.CompoundTag();
            wait.putString("Id", "create:delay");
            net.minecraft.nbt.CompoundTag waitData = new net.minecraft.nbt.CompoundTag();
            waitData.putInt("Value", TRAIN_WAIT);
            waitData.putInt("TimeUnit", 1);
            wait.put("Data", waitData);
            net.minecraft.nbt.ListTag any = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag all = new net.minecraft.nbt.ListTag();
            all.add(wait);
            any.add(all);
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.put("Instruction", instruction);
            entry.put("Conditions", any);
            entries.add(entry);
        }
        net.minecraft.nbt.CompoundTag schedule = new net.minecraft.nbt.CompoundTag();
        schedule.put("Entries", entries);
        schedule.putBoolean("Cyclic", true);
        return schedule;
    }

    /** {@code /stress cleanup}: removes every train a train line made that is still in Create's records (a crash left it). */
    public static int cleanupTrains() {
        int n = 0;
        for (Object train : Calls.trains()) {
            if (Calls.trainName(train).startsWith(TRAIN_PREFIX)) {
                Calls.removeTrain(train);
                n++;
            }
        }
        return n;
    }

    /** The calls into Create's own code, by reflection, so nothing of Create loads unless it is there. */
    static final class Calls {
        private Calls() {
        }

        /** Sets a creative motor's speed (its {@code generatedSpeed} value), as a player does with its dial. */
        static void motorSpeed(BlockEntity be, int rpm) {
            if (be == null) {
                return;
            }
            try {
                Object behaviour = be.getClass().getField("generatedSpeed").get(be);
                behaviour.getClass().getMethod("setValue", int.class).invoke(behaviour, rpm);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not set a Create motor's speed at {}: {}", be.getBlockPos(), e.toString());
            }
        }

        /**
         * Forgets everything a belt carries (its inventory's list, and what waits to go on), so removing it drops
         * nothing, not even the empty stacks taking items off through Forge's hookup leaves behind. False when it could
         * not.
         */
        @SuppressWarnings("unchecked")
        static boolean clearBelt(BlockEntity be) {
            if (be == null) {
                return false;
            }
            try {
                Object inventory = be.getClass().getMethod("getInventory").invoke(be);
                if (inventory == null) {
                    return false;
                }
                ((java.util.List<Object>) inventory.getClass().getMethod("getTransportedItems").invoke(inventory)).clear();
                java.lang.reflect.Field waiting = inventory.getClass().getDeclaredField("toInsert");
                waiting.setAccessible(true);
                ((java.util.List<Object>) waiting.get(inventory)).clear();
                return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.debug("Could not empty the belt at {}: {}", be.getBlockPos(), e.toString());
                return false;
            }
        }

        /**
         * Makes a bearing or piston forget its moving contraption ({@code movedContraption}, {@code running}), so
         * removing it does not put the contraption's blocks down.
         */
        static void forgetContraption(BlockEntity be) {
            if (be == null) {
                return;
            }
            for (Class<?> c = be.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    try {
                        if (f.getName().equals("movedContraption")) {
                            f.setAccessible(true);
                            f.set(be, null);
                        } else if (f.getName().equals("running") && f.getType() == boolean.class) {
                            f.setAccessible(true);
                            f.setBoolean(be, false);
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        BSPServerStressTest.LOGGER.debug("Could not clear {} of {}: {}", f.getName(), be.getBlockPos(), e.toString());
                    }
                }
            }
        }

        /** Aims a station at a track spot ({@code TargetTrack}, an offset; {@code TargetDirection}), as its item does when placed. */
        static void target(BlockEntity station, BlockPos offset, boolean positive) {
            if (station == null) {
                return;
            }
            net.minecraft.nbt.CompoundTag tag = station.saveWithoutMetadata();
            tag.put("TargetTrack", net.minecraft.nbt.NbtUtils.writeBlockPos(offset));
            tag.putBoolean("TargetDirection", positive);
            station.load(tag);
            station.setChanged();
        }

        static boolean hasStation(BlockEntity station) {
            try {
                return station != null && station.getClass().getMethod("getStation").invoke(station) != null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return false;
            }
        }

        static void nameStation(BlockEntity station, String name) {
            try {
                station.getClass().getMethod("updateName", String.class).invoke(station, name);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.debug("Could not name the station at {}: {}", station.getBlockPos(), e.toString());
            }
        }

        static boolean assembling(BlockEntity station) {
            try {
                return (Boolean) station.getClass().getMethod("isAssembling").invoke(station);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return false;
            }
        }

        static void enterAssembly(BlockEntity station) {
            try {
                // the check first: entering with no player is only safe where it will not fail (a failure messages the player)
                if ((Boolean) station.getClass().getMethod("tryEnterAssemblyMode").invoke(station)) {
                    station.getClass().getMethod("enterAssemblyMode", net.minecraft.server.level.ServerPlayer.class).invoke(station, (Object) null);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not start assembling a train at {}: {}", station.getBlockPos(), e.toString());
            }
        }

        /** Assembles what stands at the station (in assembly mode), then names the train. */
        static void assemble(BlockEntity station, String name) {
            try {
                Class<?> c = station.getClass();
                c.getMethod("assemble", java.util.UUID.class).invoke(station, java.util.UUID.randomUUID());
                Object train = trainAt(station);
                if (train != null) {
                    train.getClass().getField("name").set(train, net.minecraft.network.chat.Component.literal(name));
                    // a station still in assembly mode is no destination: the train could never come back to it
                    if ((Boolean) c.getMethod("isAssembling").invoke(station)) {
                        c.getMethod("exitAssemblyMode").invoke(station);
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not assemble a train at {}: {}", station.getBlockPos(), e.toString());
            }
        }

        static Object trainAt(BlockEntity station) throws ReflectiveOperationException {
            Object global = station.getClass().getMethod("getStation").invoke(station);
            return global == null ? null : global.getClass().getMethod("getPresentTrain").invoke(global);
        }

        @SuppressWarnings("unchecked")
        static java.util.Collection<Object> trains() {
            try {
                Object railways = Class.forName("com.simibubi.create.Create").getField("RAILWAYS").get(null);
                return new ArrayList<>(((java.util.Map<java.util.UUID, Object>) railways.getClass().getField("trains").get(railways)).values());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return List.of();
            }
        }

        static String trainName(Object train) {
            try {
                return ((net.minecraft.network.chat.Component) train.getClass().getField("name").get(train)).getString();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return "";
            }
        }

        static Object trainNamed(String name) {
            for (Object t : trains()) {
                if (trainName(t).equals(name)) {
                    return t;
                }
            }
            return null;
        }

        /** Seats a mob on a Create seat, as a player leading it there does. */
        static void sitDown(Level level, BlockPos seat, Entity mob) {
            try {
                Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatBlock")
                        .getMethod("sitDown", Level.class, BlockPos.class, Entity.class).invoke(null, level, seat, mob);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not seat a conductor at {}: {}", seat, e.toString());
            }
        }

        /** Takes a train out of Create's records and discards its carriages and what rides them. */
        static void removeTrain(Object train) {
            if (train == null) {
                return;
            }
            try {
                for (Object carriage : (java.util.List<?>) train.getClass().getField("carriages").get(train)) {
                    Object entity = carriage.getClass().getMethod("anyAvailableEntity").invoke(carriage);
                    if (entity instanceof Entity e) {
                        e.getIndirectPassengers().forEach(Entity::discard);
                        e.discard();
                    }
                }
                Object railways = Class.forName("com.simibubi.create.Create").getField("RAILWAYS").get(null);
                java.util.UUID id = (java.util.UUID) train.getClass().getField("id").get(train);
                railways.getClass().getMethod("removeTrain", java.util.UUID.class).invoke(railways, id);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not remove a Create train: {}", e.toString());
            }
        }

        /** Lays a belt between two shafts, as a player does with a belt item. */
        static void createBelt(Level level, BlockPos start, BlockPos end) {
            try {
                Class.forName("com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem")
                        .getMethod("createBelts", Level.class, BlockPos.class, BlockPos.class)
                        .invoke(null, level, start, end);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not lay a Create belt from {} to {}: {}", start, end, e.toString());
            }
        }
    }
}
