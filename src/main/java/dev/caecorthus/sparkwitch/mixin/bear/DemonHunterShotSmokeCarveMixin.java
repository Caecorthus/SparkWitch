package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.demonhunter.DemonHunterShootC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BEAR bullet tunnel for the NoellesRoles Demon Hunter pistol at its receiver's {@code recordItemUse} anchor (reached
 * only for an accepted shot with a bullet left). Same ray rule as the Wathe revolver. Visual only, never cancels.
 * NoellesRoles/Fabric member: class-level {@code remap = false}.
 * NoellesRoles 猎魔枪的 BEAR 弹道通道，挂在其接收器的 {@code recordItemUse} 锚点（只有已接受且尚有子弹的射击才会到达）。射线规则
 * 与 Wathe 左轮相同。纯视觉，从不取消。NoellesRoles/Fabric 成员：类级 {@code remap = false}。
 */
@Mixin(value = DemonHunterShootC2SPacket.Receiver.class, remap = false)
public abstract class DemonHunterShotSmokeCarveMixin {
    @Inject(method = "receive(Lorg/agmas/noellesroles/demonhunter/DemonHunterShootC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$carveBulletTunnel(DemonHunterShootC2SPacket payload, ServerPlayNetworking.Context context,
                                             CallbackInfo ci) {
        RangedSmokeCarve.gunShot(context.player(), payload.target(), BearRicochetRules.DEMON_HUNTER_RANGE);
    }
}
