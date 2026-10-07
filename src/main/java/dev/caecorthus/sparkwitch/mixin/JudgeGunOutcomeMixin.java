package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRuntime;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Denied lethal shots still fire/use ammo/cooldown, but cannot schedule false-kill punishments. / 禁杀不禁开枪消耗，但不制造误杀惩罚。 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class JudgeGunOutcomeMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter;shouldPunish(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/event/ShouldPunishGunShooter$PunishResult;"))
    private ShouldPunishGunShooter.PunishResult sparkwitch$skipFalsePunishment(
            ShouldPunishGunShooter event, PlayerEntity shooter, PlayerEntity victim,
            Operation<ShouldPunishGunShooter.PunishResult> original,
            @Share("judgeBlockedShot") LocalBooleanRef blocked) {
        boolean denied = shooter instanceof ServerPlayerEntity serverShooter
                && JudgeRuntime.blocksKill(serverShooter.getServerWorld(), shooter.getUuid(), victim.getUuid());
        blocked.set(denied);
        return denied ? ShouldPunishGunShooter.PunishResult.cancel() : original.call(event, shooter, victim);
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerMoodComponent;setMood(F)V"))
    private void sparkwitch$skipFalseKillMoodPenalty(PlayerMoodComponent mood, float amount, Operation<Void> original,
                                                     @Share("judgeBlockedShot") LocalBooleanRef blocked) {
        if (!blocked.get()) original.call(mood, amount);
    }
}
