package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
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
 * External seam (Fabric networking internals, as the Fear and Control Expert guards): the third wrapper on
 * ServerPlayNetworkAddon#receive. It wraps the single main-thread hand-off ({@code MinecraftServer#execute}, a vanilla
 * target, hence {@code remap = true} on the {@code @At} inside this {@code remap = false} class) so a
 * {@link SeekerRemoteRules#BLOCKED_WHILE_VIEWING} payload from a locked sender is dropped on the server thread,
 * atomically with the handler it would have run. Every other payload is scheduled unchanged; each of the three
 * wrappers keeps its own list. Server authority: a modified client cannot act through its frozen body.
 * 外部接缝（Fabric 网络内部实现，与恐惧、控场专家拦截相同）：ServerPlayNetworkAddon#receive 上的第三个包装器。
 * 它包装唯一一次移交主线程的调用（{@code MinecraftServer#execute} 为原版目标，因此在这个 {@code remap = false} 的类中
 * 该 {@code @At} 显式写 {@code remap = true}），使被锁定发送者的 {@link SeekerRemoteRules#BLOCKED_WHILE_VIEWING} 数据包
 * 在服务端线程上、与其原本要执行的处理器原子地被丢弃。其他数据包原样调度；三个包装器各自维护名单。
 * 服务端权威：修改过的客户端无法经由冻结的本体行动。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class SeekerSessionPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",
                    remap = true)
    )
    private void sparkwitch$dropPayloadWhileViewing(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!SeekerRemoteRules.isBlockedWhileViewing(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!SeekerRemoteSessionService.isLocked(context.player())) {
                handler.run();
            }
        });
    }
}
