package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
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
 * Client-only: snaps the eye height when the camera switches between the body (1.62) and the crow (0.25) instead of
 * vanilla's eased {@code cameraY}/{@code lastCameraY} blend (the crow spawns with its eye at the body's eye, so the
 * switch is seamless). The per-camera flag lets the first update after the flight snap back to the body too; outside a
 * session and its exit edge nothing is touched. Runs at HEAD, before Wathe's RETURN screen shake.
 * 纯客户端：镜头在本体（1.62）与乌鸦（0.25）之间切换时瞬间对齐眼高，而不是原版对 cameraY/lastCameraY 的缓动（乌鸦生成时眼睛与本体眼睛
 * 重合，切换因此无缝）。每个镜头实例的标记使飞行结束后的第一次更新也能对齐回本体；会话及其退出沿之外不做任何改动。在 HEAD 执行，早于
 * Wathe 在 RETURN 的镜头晃动。
 */
@Mixin(Camera.class)
public abstract class BlackRavenCrowCameraMixin {
    @Shadow
    private Entity focusedEntity;
    @Shadow
    private float cameraY;
    @Shadow
    private float lastCameraY;
    @Unique
    private boolean sparkwitch$crowFocus;

    @Inject(method = "update", at = @At("HEAD"))
    private void sparkwitch$snapCrowEyeHeight(BlockView area, Entity focus, boolean thirdPerson, boolean inverseView,
                                             float tickDelta, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive() && !this.sparkwitch$crowFocus) {
            return;
        }
        this.sparkwitch$crowFocus = BlackRavenCrowPilotClient.isActive();
        if (focus != null && focus != this.focusedEntity) {
            float eyeHeight = focus.getStandingEyeHeight();
            this.cameraY = eyeHeight;
            this.lastCameraY = eyeHeight;
        }
    }
}
