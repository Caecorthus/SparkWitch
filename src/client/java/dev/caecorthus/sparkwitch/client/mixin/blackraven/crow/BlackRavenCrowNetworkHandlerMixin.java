package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server camera writes and player replacement during Raven Form (Seeker remote-view pattern). Every hook sits right
 * AFTER {@code NetworkThreadUtils.forceMainThread}, never at HEAD (the Netty-thread pass would race the render thread).
 * {@code onSetCameraEntity}: a server camera writer (Taotie, Last Stand, Depression) takes the camera, so the crow view
 * yields before vanilla applies it (an unloaded target, which vanilla ignores, hands the camera back to the body
 * instead). {@code onPlayerRespawn} / {@code onGameJoin}: reset before vanilla replaces the player. First statement:
 * the {@code isActive()} gate.
 * 化鸦期间的服务端镜头写入与玩家对象替换（搜寻者遥控视角模式）。每个钩子都位于 forceMainThread 之后，绝不在 HEAD（Netty 线程那一遍会与
 * 渲染线程竞争）。onSetCameraEntity：服务端镜头写入方（饕餮、背水一战、抑郁）接管镜头，乌鸦视角在原版应用之前让出（目标未加载、原版将忽略
 * 时改为归还本体）。onPlayerRespawn / onGameJoin：在原版替换玩家之前重置。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class BlackRavenCrowNetworkHandlerMixin {
    private static final String FORCE_MAIN_THREAD = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread("
            + "Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;"
            + "Lnet/minecraft/util/thread/ThreadExecutor;)V";

    @Inject(method = "onSetCameraEntity", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$crowYieldsToServerCamera(SetCameraEntityS2CPacket packet, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowPilotClient.yieldCamera(packet.getEntity(((ClientPlayNetworkHandler) (Object) this).getWorld()));
    }

    @Inject(method = "onPlayerRespawn", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$crowResetOnRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowPilotClient.forceExit();
    }

    @Inject(method = "onGameJoin", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void sparkwitch$crowResetOnJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowPilotClient.forceExit();
    }
}
