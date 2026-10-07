package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianRecordedAction;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server gun receiver at Wathe's {@code recordItemUse} anchor, i.e. only for a shot Wathe accepted (after its spectator,
 * gun-tag, cooldown and spent-derringer checks and after SparkTraits' Last Escape HEAD cancel): records the shot for a
 * recording Magician, and ends a validated puppet target. A puppet id never resolves to Wathe's player target, so Wathe
 * then finishes the shot as a miss (sound, muzzle flash, cooldown; no punishment, no mood loss). Never cancels.
 * Wathe member: class-level {@code remap = false}.
 * 服务端枪械接收器的 Wathe {@code recordItemUse} 锚点，即只处理 Wathe 已接受的射击（位于其旁观、枪械标签、冷却与德林加已用检查
 * 之后，也在 SparkTraits 最后逃脱的 HEAD 取消之后）：为录制中的魔术师记录这一枪，并结束经校验的皮套目标。皮套 id 永远不会
 * 解析为 Wathe 的玩家目标，因此 Wathe 随后按未命中收尾（声音、枪口火光、冷却；无惩罚、不扣理智）。从不取消。
 * Wathe 成员：类级 {@code remap = false}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class MagicianGunShootPayloadReceiverMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkwitch$recordShotAndHitPuppet(GunShootPayload payload, ServerPlayNetworking.Context context,
                                                    CallbackInfo ci) {
        ServerPlayerEntity shooter = context.player();
        MagicianServerHooks.record(shooter, MagicianRecordedAction.Type.GUN_SHOOT);
        Entity target = shooter.getServerWorld().getEntityById(payload.target());
        if (target instanceof MagicianPlaybackEntity) {
            MagicianPuppetHits.onGunPayload(shooter, target, shooter.getMainHandStack());
        }
    }
}
