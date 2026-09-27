package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server camera writes and player replacement. Every hook sits right AFTER
 * {@code NetworkThreadUtils.forceMainThread}, never at HEAD: at HEAD the handler first runs on the Netty thread and
 * would race the render thread. {@code onSetCameraEntity}: a server camera writer (Taotie, Last Stand, Depression)
 * takes the camera, so the view yields without restoring it before vanilla applies the new camera.
 * {@code onPlayerRespawn} / {@code onGameJoin} (respawn, dimension or proxy server change): reset before vanilla
 * replaces the player. Each handler starts with the isActive() gate.
 * 服务端相机写入与玩家对象替换。每个钩子都位于 {@code NetworkThreadUtils.forceMainThread} 之后，绝不在 HEAD：
 * 在 HEAD 时处理器会先在 Netty 线程上执行一次，与渲染线程竞争。{@code onSetCameraEntity}：服务端相机写入方
 * （饕餮、最后一搏、抑郁）接管相机，视角在原版应用新相机之前让出且不归还。{@code onPlayerRespawn} / {@code onGameJoin}
 * （重生、切换维度或代理切服）：在原版替换玩家之前重置。每个处理器都以 isActive() 门槛开头。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SeekerRemoteNetworkHandlerMixin {
    private static final String FORCE_MAIN_THREAD = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread("
            + "Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;"
            + "Lnet/minecraft/util/thread/ThreadExecutor;)V";

    @Inject(method = "onSetCameraEntity", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$yieldToServerCamera(SetCameraEntityS2CPacket packet, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        SeekerRemoteViewClient.yieldCamera();
    }

    @Inject(method = "onPlayerRespawn", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$resetOnRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        SeekerRemoteViewClient.forceExit();
    }

    @Inject(method = "onGameJoin", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$resetOnJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        SeekerRemoteViewClient.forceExit();
    }
}
