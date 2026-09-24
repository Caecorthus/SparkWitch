package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A superseding teleport ends the saved trajectory; the Swapper adapter binds the new one after this returns.
 * 后续传送结束旧轨迹；交换适配器在本回调返回之后再绑定新轨迹。 */
@Mixin(ServerPlayerEntity.class)
public abstract class JudgeTeleportRecoveryMixin {
    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void sparkwitch$clearSuccessfulTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) JudgeTrainFallAttribution.superseded((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/entity/Entity;", at = @At("RETURN"))
    private void sparkwitch$clearSuccessfulWorldTeleport(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) JudgeTrainFallAttribution.superseded((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = {"requestTeleport(DDD)V", "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V"}, at = @At("TAIL"))
    private void sparkwitch$clearCompletedTeleport(CallbackInfo ci) {
        JudgeTrainFallAttribution.superseded((ServerPlayerEntity) (Object) this);
    }
}
