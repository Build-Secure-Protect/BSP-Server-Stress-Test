package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.bots.Bots;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.entities.EntityOption;
import com.mrgregles.bsp_server_stress_test.entities.Spawns;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * ST-12, crowds: the mob, item and orb options; real zombies spawned tagged and persistent over held chunks, stepped
 * down and removed; items and orbs that neither merge nor get picked up; a tagged stray from an earlier run is refused;
 * cleanup takes every tagged entity; bots take no harm.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class EntityGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final String ZOMBIES = "entities.minecraft.zombie";

    @GameTest(template = EMPTY)
    public void mobs_items_and_orbs_are_options(GameTestHelper h) {
        for (String id : List.of(ZOMBIES, "entities.minecraft.cow", EntityOption.ITEMS, EntityOption.XP)) {
            if (Options.byId(id) == null || Catalogue.byId(id) == null || !Catalogue.byId(id).ready()) {
                h.fail(id + " is not a ready option");
                return;
            }
        }
        if (Options.byId("entities.minecraft.wither") != null || Options.byId("entities.minecraft.ender_dragon") != null) {
            h.fail("a left-out mob has an option");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "entities_mobs", timeoutTicks = 100)
    public void zombies_are_spawned_tagged_and_persistent_then_removed(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption zombies = Options.byId(ZOMBIES);
        try {
            zombies.apply(server, 3);
            List<Entity> made = Spawns.entities(ZOMBIES);
            if (made.size() != 3) {
                h.fail("spawned " + made.size());
                return;
            }
            for (Entity e : made) {
                if (!(e instanceof Zombie z) || !z.isAlive() || !z.getTags().contains(Spawns.TAG) || !z.isPersistenceRequired()
                        || !Spawns.heldUnder(z)) {
                    h.fail("a zombie is not alive, tagged, persistent and held: " + e);
                    return;
                }
            }
            zombies.apply(server, 1);
            if (Spawns.count(ZOMBIES) != 1 || !made.get(2).isRemoved() || made.get(0).isRemoved()) {
                h.fail("stepping down to 1 left " + Spawns.count(ZOMBIES));
                return;
            }
            zombies.release(server);
            if (made.stream().anyMatch(e -> !e.isRemoved()) || ChunkHold.held(Spawns.CHUNK_GROUP) != 0) {
                h.fail("letting go left zombies or held chunks");
                return;
            }
        } finally {
            Spawns.releaseAll(server);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "entities_items", timeoutTicks = 100)
    public void items_and_orbs_stay_as_many_as_were_spawned(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Options.byId(EntityOption.ITEMS).apply(server, 10);
        Options.byId(EntityOption.XP).apply(server, 10);
        h.runAfterDelay(25, () -> {
            try {
                if (Spawns.count(EntityOption.ITEMS) != 10 || Spawns.count(EntityOption.XP) != 10) {
                    h.fail("after 25 ticks: " + Spawns.count(EntityOption.ITEMS) + " items, " + Spawns.count(EntityOption.XP) + " orbs");
                    return;
                }
                if (Spawns.entities(EntityOption.ITEMS).stream().anyMatch(e -> e.isRemoved() || e.getType() != EntityType.ITEM)) {
                    h.fail("an item merged or went");
                    return;
                }
            } finally {
                Spawns.releaseAll(server);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "entities_stray")
    public void a_tagged_entity_from_an_earlier_run_is_refused(GameTestHelper h) {
        Zombie stray = EntityType.ZOMBIE.create(h.getLevel());
        BlockPos at = h.absolutePos(new BlockPos(5, 2, 5));
        stray.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        stray.addTag(Spawns.TAG);
        boolean added = h.getLevel().addFreshEntity(stray);
        if (added || h.getLevel().getEntity(stray.getUUID()) != null) {
            stray.discard();
            h.fail("a tagged stray was let into the world");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "entities_cleanup")
    public void cleanup_takes_every_tagged_entity(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Options.byId(ZOMBIES).apply(server, 2);
        List<Entity> made = Spawns.entities(ZOMBIES);
        Mob loose = (Mob) h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        loose.addTag(Spawns.TAG); // as if loaded from an earlier run while it was still let in
        int n = Spawns.cleanup(server);
        if (n < 3 || made.stream().anyMatch(e -> !e.isRemoved()) || !loose.isRemoved() || Spawns.total() != 0) {
            loose.discard();
            Spawns.releaseAll(server);
            h.fail("cleanup removed " + n);
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "entities_bot")
    public void bots_take_no_harm(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = BotGameTests.pauseSpawning(server);
        try {
            Bots.setCount(server, Bots.Behaviour.STAND, 1);
            var bot = Bots.online().get(0).player();
            if (!bot.getAbilities().invulnerable || bot.hurt(bot.damageSources().generic(), 5) || bot.getHealth() < bot.getMaxHealth()) {
                h.fail("the bot can be hurt");
                return;
            }
        } finally {
            Bots.releaseAll(server);
            BotGameTests.restoreSpawning(server, spawning);
        }
        h.succeed();
    }
}
