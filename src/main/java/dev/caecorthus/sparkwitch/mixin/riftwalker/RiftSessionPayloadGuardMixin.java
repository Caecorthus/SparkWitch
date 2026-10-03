package dev.caecorthus.sparkwitch.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
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
 * External seam (Fabric networking internals, the Seeker guard's shape): a wrapper on the single main-thread hand-off
 * of {@code ServerPlayNetworkAddon#receive} ({@code MinecraftServer#execute}, a vanilla target, hence
 * {@code remap = true} on the {@code @At} inside this {@code remap = false} class). A
 * {@link RiftSessionRules#BLOCKED_WHILE_INSIDE} payload from a player inside a Rift Gate is dropped on the server
 * thread, atomically with the handler it would have run, so skills, the shop and NoellesRoles abilities (which do not
 * reject spectator senders) cannot act from inside. {@code rift_hop} and {@code rift_exit} are never listed; every
 * other payload is scheduled unchanged, and the Fear, Control Expert and Seeker wrappers keep their own lists.
 * 外部接缝（Fabric 网络内部实现，沿用搜寻者拦截器的形状）：包装 {@code ServerPlayNetworkAddon#receive} 中唯一一次移交主线程的
 * 调用（{@code MinecraftServer#execute} 为原版目标，因此在这个 {@code remap = false} 的类中该 {@code @At} 显式写
 * {@code remap = true}）。位于裂隙门内的玩家发送的 {@link RiftSessionRules#BLOCKED_WHILE_INSIDE} 数据包在服务端线程上、与其原本
 * 要执行的处理器原子地被丢弃，因此技能、商店与 NoellesRoles 能力（它们不拒绝旁观者发送者）都无法在门内生效。
 * {@code rift_hop} 与 {@code rift_exit} 从不在列；其他数据包原样调度，恐惧、控场专家与搜寻者包装器各自维护名单。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class RiftSessionPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",
                    remap = true)
    )
    private void sparkwitch$dropPayloadInsideRiftGate(
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
            if (!RiftSessionService.isInside(context.player())) {
                handler.run();
            }
        });
    }
}
