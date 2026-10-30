package dev.caecorthus.sparkwitch.mixin.knight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightDuelSkillLock;
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
 * External seam (Fabric networking internals, the Seeker and Rift guards' shape): a wrapper on the single main-thread
 * hand-off of {@code ServerPlayNetworkAddon#receive} ({@code MinecraftServer#execute}, a vanilla target, hence
 * {@code remap = true} on the {@code @At} inside this {@code remap = false} class). A
 * {@link KnightDuelSkillLock#BLOCKED_WHILE_DUELING} (G skill) or {@link KnightDuelSkillLock#SEALED_WEAPON_PAYLOADS}
 * (payload weapon) payload from either duelist is dropped silently on the server thread, atomically with the handler
 * it would have run; the Netty thread only picks payloads by id. Every other payload is scheduled unchanged, and the
 * Fear, stun, Seeker, Rift and crow wrappers keep their own lists. Server authority: a modified client or a rebound key
 * cannot fire a G skill or a sealed payload weapon during a duel.
 * 外部接缝（Fabric 网络内部实现，沿用搜寻者与裂隙拦截器的形状）：包装 {@code ServerPlayNetworkAddon#receive} 中唯一一次
 * 移交主线程的调用（{@code MinecraftServer#execute} 为原版目标，因此在这个 {@code remap = false} 的类中该 {@code @At}
 * 显式写 {@code remap = true}）。任一决斗者发送的 {@link KnightDuelSkillLock#BLOCKED_WHILE_DUELING}（G 键技能）或
 * {@link KnightDuelSkillLock#SEALED_WEAPON_PAYLOADS}（数据包武器）数据包在服务端线程上、与其原本要执行的处理器原子地被
 * 静默丢弃；Netty 线程只按 id 挑选数据包。其他数据包原样调度，恐惧、眩晕、搜寻者、裂隙与乌鸦包装器各自维护名单。服务端
 * 权威：修改过的客户端或改绑的按键都无法在决斗中触发 G 键技能或被封印的数据包武器。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class KnightDuelSkillPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V",
                    remap = true)
    )
    private void sparkwitch$dropDuelistSkillPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!KnightDuelSkillLock.isBlockedWhileDueling(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!KnightDuelSkillLock.isLocked(context.player())) {
                handler.run();
            }
        });
    }
}
