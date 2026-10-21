package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.explosions.ExplosionOption;
import com.mrgregles.bsp_server_stress_test.explosions.Explosions;
import com.mrgregles.bsp_server_stress_test.run.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * ST-13, explosions: the options; a kept explosion leaves the blocks and a breaking one does not; a TNT cluster; a TNT
 * block set off by its redstone block, which is then taken away; a block that does not answer to redstone is a dud and
 * is taken away; switching options on and off holds and frees the blast area.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class ExplosionGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    private static void clearDrops(GameTestHelper h) {
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(24, 10, 24)));
        for (Entity e : h.getLevel().getEntitiesOfClass(Entity.class, box, e -> e instanceof ItemEntity || e instanceof PrimedTnt)) {
            e.discard();
        }
    }

    @GameTest(template = EMPTY)
    public void explosions_are_options(GameTestHelper h) {
        for (String id : List.of(ExplosionOption.VANILLA, ExplosionOption.TNT_CHAIN, "explosions.minecraft.tnt")) {
            if (Options.byId(id) == null || !Catalogue.byId(id).ready()) {
                h.fail(id + " is not a ready option");
                return;
            }
        }
        if (!Explosions.due(100, 100) || Explosions.due(99, 100)) {
            h.fail("the timing of blasts is off");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "explosions_keep")
    public void a_kept_explosion_leaves_the_blocks_and_a_breaking_one_does_not(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos dirt = new BlockPos(12, 3, 12);
        h.setBlock(dirt, Blocks.DIRT);
        Explosions.explodeAt(level, h.absolutePos(new BlockPos(12, 2, 12)), 2, false);
        if (!level.getBlockState(h.absolutePos(dirt)).is(Blocks.DIRT)) {
            h.fail("a kept explosion broke the dirt");
            return;
        }
        Explosions.explodeAt(level, h.absolutePos(new BlockPos(12, 2, 12)), 2, true);
        boolean broken = !level.getBlockState(h.absolutePos(dirt)).is(Blocks.DIRT);
        clearDrops(h);
        if (!broken) {
            h.fail("a breaking explosion left the dirt");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "explosions_tnt")
    public void a_tnt_cluster_is_lit_tnt_close_together(GameTestHelper h) {
        List<PrimedTnt> tnt = Explosions.primeCluster(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)), 8);
        boolean ok = tnt.size() == 8 && tnt.stream().allMatch(t -> t.isAlive() && t.getFuse() >= 40 && t.getFuse() < 60)
                && tnt.stream().allMatch(t -> t.distanceTo(tnt.get(0)) < 4);
        tnt.forEach(Entity::discard);
        if (!ok) {
            h.fail("the cluster is not 8 lit TNT close together");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "explosions_fire", timeoutTicks = 400)
    public void a_tnt_block_goes_off_with_its_redstone_block_and_a_dud_is_taken_away(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos tntAt = h.absolutePos(new BlockPos(4, 2, 4));
        BlockPos dudAt = h.absolutePos(new BlockPos(18, 2, 18));
        int dudsBefore = Explosions.duds();
        Explosions.placeAndFire(level, tntAt, Blocks.TNT);
        Explosions.placeAndFire(level, dudAt, Blocks.BONE_BLOCK);
        if (!level.getBlockState(tntAt).isAir() || level.getEntitiesOfClass(PrimedTnt.class, new AABB(tntAt).inflate(2)).isEmpty()) {
            clearDrops(h);
            h.fail("the TNT block did not go off");
            return;
        }
        clearDrops(h);
        h.runAfterDelay(4, () -> {
            if (level.getBlockState(tntAt.above()).is(Blocks.REDSTONE_BLOCK)) {
                h.fail("the redstone block stayed");
                return;
            }
            h.runAfterDelay(210, () -> {
                if (!level.getBlockState(dudAt).isAir() || level.getBlockState(dudAt.above()).is(Blocks.REDSTONE_BLOCK)
                        || Explosions.duds() <= dudsBefore) {
                    h.fail("the dud was not taken away and counted");
                    return;
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "explosions_area")
    public void switching_an_explosion_on_and_off_holds_and_frees_the_blast_area(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var vanilla = Options.byId(ExplosionOption.VANILLA);
        try {
            vanilla.apply(server, 8);
            if (Explosions.level(ExplosionOption.VANILLA) != 8 || ChunkHold.held(Explosions.CHUNK_GROUP) == 0) {
                h.fail("on: power " + Explosions.level(ExplosionOption.VANILLA) + ", held " + ChunkHold.held(Explosions.CHUNK_GROUP));
                return;
            }
            vanilla.release(server);
            if (Explosions.level(ExplosionOption.VANILLA) != 0 || ChunkHold.held(Explosions.CHUNK_GROUP) != 0) {
                h.fail("off: the blast area is still held");
                return;
            }
        } finally {
            Explosions.releaseAll(server);
        }
        h.succeed();
    }
}
