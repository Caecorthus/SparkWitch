package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BEAR bullet tunnel for the Wathe revolver family (revolver, derringer and every SparkWitch police gun that rides this
 * receiver) at its {@code recordItemUse} anchor, i.e. only for a shot Wathe accepted (after its spectator, gun-tag,
 * cooldown and spent-derringer checks). The tunnel runs from the shooter's eyes to where the bullet entered the target,
 * or along the server rotation to the first block on a miss. Visual only, never cancels; the kill itself is decided
 * later in the same method. Wathe member: class-level {@code remap = false}.
 * Wathe 左轮系（左轮、德林加，以及经由该接收器的所有 SparkWitch 警用枪）的 BEAR 弹道通道，挂在其 {@code recordItemUse}
 * 锚点，即只处理 Wathe 已接受的射击（位于旁观、枪械标签、冷却与德林加已用检查之后）。通道从射手眼睛到子弹进入目标处，未命中时
 * 沿服务端朝向到第一个方块。纯视觉，从不取消；击杀本身稍后在同一方法内决定。Wathe 成员：类级 {@code remap = false}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class GunShootPayloadSmokeCarveMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$carveBulletTunnel(GunShootPayload payload, ServerPlayNetworking.Context context,
                                             CallbackInfo ci) {
        RangedSmokeCarve.gunShot(context.player(), payload.target(), BearRicochetRules.REVOLVER_RANGE);
    }
}
