package dev.caecorthus.sparkwitch.mixin.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The shotgun sits in {@code wathe:guns} only for Wathe's gun treatment; it fires through its own server-side
 * {@code use()} and never sends {@code wathe:gunshoot}. Wathe's receiver trusts the client-chosen target (65 blocks,
 * no line of sight, zero cooldown for unlisted items), so any such packet with the shotgun in hand is forged.
 * 霰弹枪加入 {@code wathe:guns} 只为沿用 Wathe 的枪械处理；它由自身服务端 {@code use()} 开火，从不发送
 * {@code wathe:gunshoot}。该接收器信任客户端指定的目标（65 格、无视线检查、未登记物品零冷却），
 * 因此手持霰弹枪时收到的此类数据包必为伪造，直接丢弃。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class GunShootPayloadShotgunGuardMixin {
    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$dropForgedShotgunShot(
            GunShootPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (context.player().getMainHandStack().getItem() instanceof DoubleBarrelShotgunItem) {
            ci.cancel();
        }
    }
}
