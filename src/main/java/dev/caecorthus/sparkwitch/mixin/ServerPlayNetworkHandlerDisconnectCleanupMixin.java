package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla seam (the same target and anchor as {@code JudgeNetworkTeleportRecoveryMixin}): {@code onDisconnected} runs on
 * the server thread and goes on to {@code PlayerManager.remove}, which saves the player. The disconnect cleanups that
 * {@link ServerDisconnectCleanup} queued from the Netty thread run here at the latest, so a stripped kit or a cleared
 * component is in the saved file, as it was while those cleanups ran straight on the Netty thread. Nothing is cancelled
 * and nothing runs when no cleanup is pending.
 * 原版接缝（与 {@code JudgeNetworkTeleportRecoveryMixin} 相同的目标与锚点）：{@code onDisconnected} 在服务端线程运行，随后进入
 * 会保存玩家的 {@code PlayerManager.remove}。{@link ServerDisconnectCleanup} 从 Netty 线程排队的断线清理最迟在此运行，因此
 * 被清除的装备或被清空的组件会写入存档，与这些清理直接在 Netty 线程运行时一致。不取消任何调用；没有待处理清理时不做任何事。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerDisconnectCleanupMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void sparkwitch$runPendingDisconnectCleanup(CallbackInfo ci) {
        ServerDisconnectCleanup.runPending(player);
    }
}
