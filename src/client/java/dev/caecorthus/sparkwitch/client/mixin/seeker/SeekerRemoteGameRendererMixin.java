package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerViewFilter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only render fixes while the Seeker views remotely (every handler is gated first on
 * {@link SeekerRemoteViewClient#isActive()}, so nothing changes outside a session):
 * <ul>
 *   <li>{@code updateCrosshairTarget} RETURN (every exit, including the early ones): force a MISS and clear
 *   {@code targetedEntity}, so no mod or vanilla key acts on whatever sits in front of the car or camera. The server
 *   still denies every interaction on its own.</li>
 *   <li>{@code renderHand} HEAD: cancel, because Iris can bypass {@code setRenderHand(false)}.</li>
 *   <li>{@code render} before the single {@code Framebuffer#beginWrite(Z)} (after world rendering, outlines and the
 *   vanilla post pass, before the GUI): run the private {@link SeekerViewFilter}; release it on inactive frames.</li>
 * </ul>
 * 搜寻者遥控观看期间的纯客户端渲染修正（每个处理器第一步都判断 {@link SeekerRemoteViewClient#isActive()}，
 * 会话之外不做任何改变）：{@code updateCrosshairTarget} 在所有返回点强制为 MISS 并清空 {@code targetedEntity}，
 * 使任何模组或原版按键都无法作用于小车或摄像头前方的物体（服务端仍会独立拒绝所有交互）；{@code renderHand}
 * 在 HEAD 取消，因为 Iris 可能绕过 {@code setRenderHand(false)}；{@code render} 在唯一一次
 * {@code Framebuffer#beginWrite(Z)} 之前（世界、描边与原版后处理之后，GUI 之前）运行私有的
 * {@link SeekerViewFilter}，非激活帧则释放它。
 */
@Mixin(GameRenderer.class)
public abstract class SeekerRemoteGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void sparkwitch$forceRemoteMiss(float tickDelta, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        Entity camera = client.getCameraEntity();
        Vec3d position = camera == null ? Vec3d.ZERO : camera.getCameraPosVec(tickDelta);
        Direction facing = camera == null ? Direction.UP : Direction.getFacing(camera.getRotationVec(tickDelta));
        client.crosshairTarget = BlockHitResult.createMissed(position, facing, BlockPos.ofFloored(position));
        client.targetedEntity = null;
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideRemoteHand(CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderSeekerViewFilter(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            SeekerViewFilter.release();
            return;
        }
        SeekerViewFilter.render(tickCounter.getLastFrameDuration());
    }
}
