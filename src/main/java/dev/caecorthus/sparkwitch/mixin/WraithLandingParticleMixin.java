package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppresses only vanilla landing block particles, leaving movement effects and knockback untouched. */
@Mixin(LivingEntity.class)
public abstract class WraithLandingParticleMixin {
    @WrapOperation(
            method = "fall",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
            )
    )
    private int sparkwitch$hideWraithLandingParticles(
            ServerWorld world,
            ParticleEffect effect,
            double x,
            double y,
            double z,
            int count,
            double deltaX,
            double deltaY,
            double deltaZ,
            double speed,
            Operation<Integer> original
    ) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof PlayerEntity player
                && !WraithParticipationRules.mayGenerateGroundParticles(WraithStateService.isActive(player))) {
            return 0;
        }
        return original.call(world, effect, x, y, z, count, deltaX, deltaY, deltaZ, speed);
    }
}
