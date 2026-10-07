package dev.caecorthus.sparkwitch.mixin.controlexpert;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
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
 * External seam (Fabric networking internals, already a dependency of the existing addon guard): wraps the single
 * main-thread hand-off in {@code receive} so a denied payload from a stunned sender is dropped on the server thread,
 * atomically with the handler it would have run. Only ids in {@link ControlExpertStunRules#BLOCKED_PAYLOADS} are
 * wrapped; every other payload is scheduled unchanged. Server authority: a modified client cannot bypass the stun.
 * 外部接缝（Fabric 网络内部实现，现有网络插件拦截已依赖它）：包装 {@code receive} 中唯一一次移交主线程的调用，
 * 使被眩晕发送者的受限数据包在服务端主线程上、与其原本要执行的处理器原子地被丢弃。仅包装
 * {@link ControlExpertStunRules#BLOCKED_PAYLOADS} 中的 id；其他数据包原样调度。服务端权威：修改过的客户端无法绕过眩晕。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class ControlExpertStunPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkwitch$dropStunnedPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!ControlExpertStunRules.isBlockedPayload(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!ControlExpertStun.isStunned(context.player())) {
                handler.run();
            }
        });
    }
}
