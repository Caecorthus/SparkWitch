package dev.caecorthus.sparkwitch.mixin.dominator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotComponent;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Protects the forced-shot shooter: the shot never triggers the innocent-shot punishment or its mood cost.
 * 保护被强制开枪者：这一枪不会触发误伤惩罚或对应的理智扣减。
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class DominatorForcedShotGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter;shouldPunish(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter$PunishResult;"))
    private ShouldPunishGunShooter.PunishResult sparkwitch$protectForcedShot(
            ShouldPunishGunShooter event, PlayerEntity shooter, PlayerEntity victim,
            Operation<ShouldPunishGunShooter.PunishResult> original,
            @Share("dominatorForcedShotProtected") LocalBooleanRef protectedShot) {
        boolean protectedShooter = DominatorForcedShotComponent.isProtectedPlayer(shooter);
        protectedShot.set(protectedShooter);
        return protectedShooter
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : original.call(event, shooter, victim);
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerMoodComponent;setMood(F)V"))
    private void sparkwitch$skipForcedShotMood(
            PlayerMoodComponent mood, float amount, Operation<Void> original,
            @Share("dominatorForcedShotProtected") LocalBooleanRef protectedShot) {
        if (!protectedShot.get()) {
            original.call(mood, amount);
        }
    }
}
