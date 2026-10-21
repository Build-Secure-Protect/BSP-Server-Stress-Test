package com.mrgregles.bsp_server_stress_test.mixin;

import com.mrgregles.bsp_server_stress_test.timing.Timings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times each entity's tick (with its passengers) for {@link Timings} (owner decision D16). HEAD and RETURN injects only,
 * never a redirect (Mohist rewrites some vanilla calls); while no timing session is open it reads one flag and nothing
 * else.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Unique
    private long bspStress$entityStart;

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void bspStress$beforeEntity(Entity entity, CallbackInfo ci) {
        bspStress$entityStart = Timings.on ? System.nanoTime() : 0;
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void bspStress$afterEntity(Entity entity, CallbackInfo ci) {
        if (bspStress$entityStart != 0 && Timings.on) {
            Timings.entity(entity, System.nanoTime() - bspStress$entityStart);
        }
    }
}
