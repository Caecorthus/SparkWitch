package dev.caecorthus.sparkwitch.client.mixin.hunter;

import dev.caecorthus.sparkwitch.client.hunter.DoubleBarrelMuzzleClient;
import dev.doctor4t.wathe.util.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes Wathe's muzzle packet so the shotgun's predicted first-person flash can be checked against the shots the
 * server really fired ({@link DoubleBarrelMuzzleClient#onMuzzlePacket}). Never cancels: Wathe's own world flash runs
 * unchanged. The receiver may run on the network thread, so only the immutable shooter id is read here and the rest
 * runs on the client thread. {@code remap = false}: Wathe member.
 * 监听 Wathe 枪口数据包，用服务端真实开火的次数核对猎枪预测的第一人称火焰（{@link DoubleBarrelMuzzleClient#onMuzzlePacket}）。
 * 从不取消：Wathe 自己的世界火焰照常生成。接收器可能运行在网络线程，因此这里只读取不可变的射手 id，其余逻辑在客户端线程执行。
 * {@code remap = false}：Wathe 成员。
 */
@Mixin(value = ShootMuzzleS2CPayload.Receiver.class, remap = false)
public abstract class DoubleBarrelOwnMuzzlePacketMixin {
    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/ShootMuzzleS2CPayload;"
                    + "Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$Context;)V",
            at = @At("HEAD")
    )
    private void sparkwitch$confirmDoubleBarrelFlash(ShootMuzzleS2CPayload payload,
                                                     ClientPlayNetworking.Context context, CallbackInfo ci) {
        String shooterId = payload.shooterId();
        MinecraftClient.getInstance().execute(() -> DoubleBarrelMuzzleClient.onMuzzlePacket(shooterId));
    }
}
