package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only: snaps the eye height instantly when the camera focus switches into or out of a Seeker remote view,
 * instead of vanilla's eased {@code cameraY}/{@code lastCameraY} blend between a 1.62-block body and a 0.2-block car
 * (the NoellesRoles Spirit Walker precedent). The per-camera {@code sparkwitch$remoteFocus} flag lets the first update
 * after exit snap back to the body too; outside a session and its exit edge nothing is touched, so vanilla spectating
 * keeps its easing. Runs at HEAD, before Wathe's RETURN screen shake.
 * 纯客户端：相机焦点切入或切出搜寻者遥控视角时瞬间对齐眼高，而不是原版在 1.62 格本体与 0.2 格小车之间对
 * {@code cameraY}/{@code lastCameraY} 的缓动（参照 NoellesRoles 灵行者）。每个相机实例的
 * {@code sparkwitch$remoteFocus} 标记使退出后的第一次更新也能对齐回本体；会话及其退出沿之外不做任何改动，
 * 原版旁观保持缓动。在 HEAD 执行，早于 Wathe 在 RETURN 的镜头晃动。
 */
@Mixin(Camera.class)
public abstract class SeekerRemoteCameraMixin {
    @Shadow
    private Entity focusedEntity;
    @Shadow
    private float cameraY;
    @Shadow
    private float lastCameraY;
    @Unique
    private boolean sparkwitch$remoteFocus;

    @Inject(method = "update", at = @At("HEAD"))
    private void sparkwitch$snapRemoteEyeHeight(BlockView area, Entity focus, boolean thirdPerson,
                                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive() && !this.sparkwitch$remoteFocus) {
            return;
        }
        this.sparkwitch$remoteFocus = SeekerRemoteViewClient.isActive();
        if (focus != null && focus != this.focusedEntity) {
            float eyeHeight = focus.getStandingEyeHeight();
            this.cameraY = eyeHeight;
            this.lastCameraY = eyeHeight;
        }
    }
}
