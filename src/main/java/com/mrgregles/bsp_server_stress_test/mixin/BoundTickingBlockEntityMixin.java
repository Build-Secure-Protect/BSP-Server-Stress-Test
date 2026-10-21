package com.mrgregles.bsp_server_stress_test.mixin;

import com.mrgregles.bsp_server_stress_test.timing.Timings;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times each block entity's tick for {@link Timings} (owner decision D16). HEAD and RETURN injects only, never a redirect
 * (Mohist rewrites some vanilla calls); while no timing session is open it reads one flag and nothing else.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Unique
    private long bspStress$start;

    @Inject(method = "tick", at = @At("HEAD"))
    private void bspStress$before(CallbackInfo ci) {
        bspStress$start = Timings.on ? System.nanoTime() : 0;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bspStress$after(CallbackInfo ci) {
        if (bspStress$start != 0 && Timings.on) {
            Timings.blockEntity(blockEntity, System.nanoTime() - bspStress$start);
        }
    }
}
