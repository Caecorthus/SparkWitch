package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
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
 * instead). {@code onPlayerRespawn} / {@code onGameJoin}: reset before vanilla replaces the player. {@code onEntity}:
 * vanilla skips a relative move on a logical-side entity without advancing its tracked base, so the base of the flown
 * crow is kept exact ({@code BlackRavenCrowPilotClient.keepTrackedBase}) for the moment the view ends. First statement:
 * the {@code isActive()} gate.
 * 化鸦期间的服务端镜头写入与玩家对象替换（搜寻者遥控视角模式）。每个钩子都位于 forceMainThread 之后，绝不在 HEAD（Netty 线程那一遍会与
 * 渲染线程竞争）。onSetCameraEntity：服务端镜头写入方（饕餮、背水一战、抑郁）接管镜头，乌鸦视角在原版应用之前让出（目标未加载、原版将忽略
 * 时改为归还本体）。onPlayerRespawn / onGameJoin：在原版替换玩家之前重置。onEntity：原版对逻辑端实体的相对移动包整体跳过、不推进其
 * 追踪基准，因此被操控乌鸦的基准在此保持准确（BlackRavenCrowPilotClient.keepTrackedBase），供画面结束时使用。首条语句：isActive() 门槛。
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

    // The only isLogicalSideForUpdatingMovement call in onEntity, after forceMainThread.
    // onEntity 中唯一的该调用，位于 forceMainThread 之后。
    @WrapOperation(method = "onEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;isLogicalSideForUpdatingMovement()Z"))
    private boolean sparkwitch$crowKeepsTrackedBase(Entity entity, Operation<Boolean> original, EntityS2CPacket packet) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return original.call(entity);
        }
        boolean logicalSide = original.call(entity);
        if (logicalSide) {
            BlackRavenCrowPilotClient.keepTrackedBase(entity, packet);
        }
        return logicalSide;
    }
}
