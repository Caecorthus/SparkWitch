package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server gun receiver: breaks a validated device target at the recordItemUse anchor, i.e. after Wathe's spectator,
 * gun-tag, cooldown and spent-derringer checks (the same anchor SparkTraits uses; SparkTraits' Last Escape HEAD cancel
 * runs earlier). A device id never resolves to Wathe's {@code ServerPlayerEntity} target, so Wathe continues exactly as
 * for a miss: click and shot sounds, ammo and cooldown, no innocent-shot punishment and no mood loss. The kill call is
 * untouched (SparkTraits already owns it). Trusts the client's nearest-wins pick like Wathe; {@code SeekerDeviceHits}
 * validates weapon, distance, line of sight, aim and {@code mayBreak}. Wathe member: class-level {@code remap = false}.
 * 服务端枪械接收器：在 recordItemUse 锚点处打坏经校验的设备目标，即位于 Wathe 的旁观、枪械标签、冷却与德林加已用检查
 * 之后（与 SparkTraits 使用相同锚点；SparkTraits 最后逃脱的 HEAD 取消更早执行）。设备 id 永远不会解析为 Wathe 的
 * {@code ServerPlayerEntity} 目标，因此 Wathe 完全按未命中继续：有扳机与射击声、耗弹并进入冷却，没有误杀惩罚也不扣理智。
 * 击杀调用保持不变（已由 SparkTraits 接管）。与 Wathe 一样信任客户端的最近者选择；{@code SeekerDeviceHits} 负责校验武器、
 * 距离、视线、瞄准与 {@code mayBreak}。Wathe 成员：类级 {@code remap = false}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class SeekerGunDeviceHitMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$breakTargetedSeekerDevice(GunShootPayload payload, ServerPlayNetworking.Context context,
                                                      CallbackInfo ci) {
        ServerPlayerEntity shooter = context.player();
        Entity target = shooter.getServerWorld().getEntityById(payload.target());
        if (target instanceof SeekerDeviceEntity) {
            SeekerDeviceHits.onGunPayload(shooter, target, shooter.getMainHandStack());
        }
    }
}
