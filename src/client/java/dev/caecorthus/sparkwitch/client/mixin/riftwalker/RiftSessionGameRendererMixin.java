package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftGrayscaleFilter;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
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
 * Client-only render policy inside a gate (every handler is gated first on {@link RiftSessionClient#isActive()}):
 * <ul>
 *   <li>{@code render} before the single {@code Framebuffer#beginWrite(Z)} (after world rendering, outlines and the
 *   vanilla post pass, before the GUI — the Black Raven / Seeker point): run the private {@link RiftGrayscaleFilter}
 *   (D10); release it on inactive frames; skip frames that do not render the world.</li>
 *   <li>{@code renderHand} HEAD: cancel (Iris can bypass {@code setRenderHand(false)}).</li>
 *   <li>{@code updateCrosshairTarget} RETURN (every exit): force a MISS and clear {@code targetedEntity}, so nothing
 *   (the spectator crosshair, vanilla or mod keys) acts on what the occupant looks at.</li>
 * </ul>
 * 门内的纯客户端渲染策略（每个处理器第一步都判断 {@link RiftSessionClient#isActive()}）：{@code render} 在唯一一次
 * {@code Framebuffer#beginWrite(Z)} 之前（世界、描边与原版后处理之后，GUI 之前——与黑羽鸦/Seeker 相同的位置）运行私有的
 * {@link RiftGrayscaleFilter}（D10），非激活帧释放，不渲染世界的帧跳过；{@code renderHand} 在 HEAD 取消（Iris 可能绕过
 * {@code setRenderHand(false)}）；{@code updateCrosshairTarget} 在所有返回点强制为 MISS 并清空 {@code targetedEntity}，
 * 使任何东西（旁观者准星、原版或模组按键）都不会作用于门内的人注视的目标。
 */
@Mixin(GameRenderer.class)
public abstract class RiftSessionGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderRiftGrayscale(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            RiftGrayscaleFilter.release();
            return;
        }
        if (!tick) {
            return;
        }
        RiftGrayscaleFilter.render(tickCounter.getLastFrameDuration());
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideHandInsideRift(CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void sparkwitch$forceRiftMiss(float tickDelta, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        Entity camera = client.getCameraEntity();
        Vec3d position = camera == null ? Vec3d.ZERO : camera.getCameraPosVec(tickDelta);
        Direction facing = camera == null ? Direction.UP : Direction.getFacing(camera.getRotationVec(tickDelta));
        client.crosshairTarget = BlockHitResult.createMissed(position, facing, BlockPos.ofFloored(position));
        client.targetedEntity = null;
    }
}
