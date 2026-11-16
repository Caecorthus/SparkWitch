package dev.caecorthus.sparkwitch.mixin.hiro;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroVictimFeignService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionRules;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (Fabric networking internals, the Rift and Seeker guards' shape): a wrapper on the single main-thread
 * hand-off of {@code ServerPlayNetworkAddon#receive} ({@code MinecraftServer#execute}, a vanilla target, hence
 * {@code remap = true} on the {@code @At} inside this {@code remap = false} class). A fake-dead victim of 二阶堂希罗 is
 * a living spectator, and most skill receivers (NoellesRoles, SparkStrength, SparkWitch) only ask whether the sender is
 * alive in Wathe; so every payload a Rift Gate occupant may not send ({@link RiftSessionRules#BLOCKED_WHILE_INSIDE},
 * reused on purpose: both are living players who cannot act, and a new skill payload is classified once) is dropped
 * silently on the server thread, atomically with the handler it would have run. The Netty thread only picks payloads
 * by id. Every other payload and every other sender is scheduled unchanged. Server authority: owner rule 3, a fake-dead
 * victim cannot act.
 * 外部接缝（Fabric 网络内部实现，沿用裂隙与搜寻者拦截器的形状）：包装 {@code ServerPlayNetworkAddon#receive} 中唯一一次移交
 * 主线程的调用（{@code MinecraftServer#execute} 为原版目标，因此在这个 {@code remap = false} 的类中该 {@code @At} 显式写
 * {@code remap = true}）。二阶堂希罗的假死受害者是活着的旁观者，而多数技能接收器（NoellesRoles、SparkStrength、SparkWitch）
 * 只检查发送者在 Wathe 中是否存活；因此裂隙门内玩家不得发送的所有数据包（{@link RiftSessionRules#BLOCKED_WHILE_INSIDE}，
 * 有意复用：二者都是无法行动的存活玩家，新增技能数据包只需归类一次）都在服务端线程上、与其原本要执行的处理器原子地被静默
 * 丢弃。Netty 线程只按 id 挑选数据包。其他数据包与其他发送者原样调度。服务端权威：所有者规则 3，假死的受害者无法行动。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class HiroFeignPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",
                    remap = true)
    )
    private void sparkwitch$dropFeignedVictimPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!RiftSessionRules.isBlockedWhileInside(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!HiroVictimFeignService.isFeigned(context.player())) {
                handler.run();
            }
        });
    }
}
