package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses only Minecraft's ordinary sprint/walking ground particles for active Wraiths. */
@Mixin(Entity.class)
public abstract class WraithGroundParticleMixin {
    @Inject(method = "shouldSpawnSprintingParticles", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideWraithSprintingParticles(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof PlayerEntity player
                && !WraithParticipationRules.mayGenerateGroundParticles(WraithStateService.isActive(player))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "spawnSprintingParticles", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideWraithWalkingParticles(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof PlayerEntity player
                && !WraithParticipationRules.mayGenerateGroundParticles(WraithStateService.isActive(player))) {
            ci.cancel();
        }
    }
}
