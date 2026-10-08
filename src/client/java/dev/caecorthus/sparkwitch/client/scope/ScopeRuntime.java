package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

/**
 * Client only. The scope module's per-frame runtime behind the {@code client/mixin/scope} hooks: zoom, look scale,
 * hand hiding, and the once-per-frame update (scope shadow, lens fade, then either the PiP lens or the ZOOM_BLUR lens
 * filter) that runs right before the GUI pass. Render thread only; nothing is synced or saved.
 * 仅客户端。{@code client/mixin/scope} 钩子背后的开镜模块逐帧运行时：放大、视角缩放、隐藏手，以及紧接在 GUI 之前、
 * 每帧一次的更新（镜内阴影、镜片淡入，然后是画中画镜片或全画面放大镜片滤镜）。仅渲染线程；不同步也不保存任何内容。
 */
public final class ScopeRuntime {
    private static final long NOT_SCOPED = Long.MIN_VALUE;
    private static final ScopeShadow SHADOW = new ScopeShadow();
    private static long scopedSinceNanos = NOT_SCOPED;
    private static long lastFrameNanos;
    private static long lastZoomBlurNanos;
    private static long lastPictureInPictureNanos;
    private static float lensZoom = 1.0F;
    private static Vec3d lastCameraPos;

    private ScopeRuntime() {
    }

    /**
     * The camera player's FOV multiplier as {@code GameRenderer#updateFovMultiplier} reads it: zoomed only while scoped
     * in ZOOM_BLUR (the view gate requires the camera to be the local player); PICTURE_IN_PICTURE keeps the main view
     * at 1x and zooms the lens instead.
     * {@code GameRenderer#updateFovMultiplier} 读取到的相机玩家视场倍率：仅在全画面放大模式开镜时放大（视角条件要求相机即本地
     * 玩家）；画中画模式下主画面保持 1 倍，改为放大镜内画面。
     */
    public static float fovMultiplier(float original) {
        ScopeProfile profile = ScopeClient.activeProfile();
        if (profile == null || ScopeClient.effectiveMode() == ScopeMode.PICTURE_IN_PICTURE) {
            return original;
        }
        return original * ScopeRules.zoomFactor(profile.fovMultiplier());
    }

    /** Mouse-look scale for {@code Mouse#updateMouse}; 1 when unscoped. / 鼠标视角缩放；未开镜时为 1。 */
    public static double lookScale() {
        ScopeProfile profile = ScopeClient.activeProfile();
        return profile == null ? 1.0
                : ScopeRules.lookScale(profile.sensitivityMultiplier(),
                ScopeSettingsStore.current().sensitivityPercent());
    }

    /**
     * {@code ScopeMouseScrollMixin}: offers one wheel event to the active profile ({@link ScopeProfile#onWheel}); true
     * means it consumed the event and vanilla must skip it. Unscoped (or a profile that declines), the wheel stays
     * vanilla's.
     * {@code ScopeMouseScrollMixin}：把一次滚轮事件交给当前配置（{@link ScopeProfile#onWheel}）；返回 true 表示已被消耗，
     * 原版必须跳过。未开镜（或配置不接收）时滚轮仍归原版。
     */
    public static boolean onMouseWheel(double notches) {
        ScopeProfile profile = ScopeClient.activeProfile();
        return profile != null && profile.onWheel(notches);
    }

    public static boolean hidesHand() {
        return ScopeClient.isScoped();
    }

    /** Smoothed scope-shadow offset (lens radii, GUI axes). / 平滑后的镜内阴影偏移（镜片半径，GUI 坐标轴）。 */
    public static float shadowX() {
        return SHADOW.x();
    }

    public static float shadowY() {
        return SHADOW.y();
    }

    /**
     * {@code GameRenderer#render}, right before the GUI binds the main framebuffer: updates the shadow and fade, then
     * renders the lens of the effective mode (PiP, or ZOOM_BLUR's filter, also for a frame whose PiP render failed), and
     * releases whichever pipeline has idled past the grace period.
     * {@code GameRenderer#render} 中紧接在 GUI 绑定主帧缓冲之前：更新阴影与淡入，然后渲染当前生效模式的镜片（画中画，或全画面
     * 放大滤镜；画中画渲染失败的那一帧也用后者），并释放空闲超过宽限期的管线。
     */
    public static void beforeGui(RenderTickCounter tickCounter) {
        long now = Util.getMeasuringTimeNano();
        double frameSeconds = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) / 1.0E9;
        lastFrameNanos = now;
        boolean worldRendered = ScopePictureInPicture.consumeWorldRendered();
        MinecraftClient client = MinecraftClient.getInstance();
        ScopeProfile profile = ScopeClient.activeProfile();
        if (profile == null) {
            if (scopedSinceNanos != NOT_SCOPED) {
                scopedSinceNanos = NOT_SCOPED;
                SHADOW.reset();
                lastCameraPos = null;
                lensZoom = 1.0F;
            }
            ScopeLensFilter.idle(now - lastZoomBlurNanos);
            ScopePictureInPicture.idle(now - lastPictureInPictureNanos);
            return;
        }
        if (scopedSinceNanos == NOT_SCOPED) {
            scopedSinceNanos = now;
        }
        updateShadow(client.gameRenderer.getCamera(), frameSeconds);
        if (!worldRendered) {
            // No world this frame (beginWrite runs on every frame): nothing to put a lens on.
            // 本帧没有渲染世界（beginWrite 每帧都会执行）：没有可加镜片的画面。
            return;
        }
        float fade = ScopeRules.lensFade(now - scopedSinceNanos);
        if (ScopeClient.effectiveMode() == ScopeMode.PICTURE_IN_PICTURE) {
            // The lens zooms in from 1x like ZOOM_BLUR's eased FOV. / 镜内像全画面放大的缓动视场一样从 1 倍放大。
            lensZoom = ScopeRules.easeZoom(lensZoom, ScopeRules.zoomFactor(profile.fovMultiplier()), frameSeconds);
            lastPictureInPictureNanos = now;
            ScopeLensFilter.idle(now - lastZoomBlurNanos);
            if (ScopePictureInPicture.render(client, tickCounter, lensZoom, fade, SHADOW.x(), SHADOW.y())) {
                return;
            }
        } else {
            ScopePictureInPicture.idle(now - lastPictureInPictureNanos);
        }
        lastZoomBlurNanos = now;
        ScopeLensFilter.render(client, fade, SHADOW.x(), SHADOW.y(), tickCounter.getLastFrameDuration());
    }

    /**
     * Disconnect, login start, client stop: forget the frame state and close both lens pipelines (deferred to the render
     * thread when called elsewhere).
     * 断线、开始登录、客户端停止时：清除逐帧状态并关闭两条镜片管线（在其他线程调用时推迟到渲染线程）。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ScopeRuntime::reset);
            return;
        }
        scopedSinceNanos = NOT_SCOPED;
        lastFrameNanos = 0L;
        lastZoomBlurNanos = 0L;
        lastPictureInPictureNanos = 0L;
        lensZoom = 1.0F;
        lastCameraPos = null;
        SHADOW.reset();
        ScopeLensFilter.reset();
        ScopePictureInPicture.reset();
    }

    private static void updateShadow(Camera camera, double frameSeconds) {
        Vec3d position = camera.getPos();
        double right = 0.0;
        double up = 0.0;
        if (lastCameraPos != null) {
            Vec3d delta = position.subtract(lastCameraPos);
            double yaw = Math.toRadians(camera.getYaw());
            // The view's right axis: yaw 0 faces +Z, so right is -X. / 视角右方向：偏航 0 面向 +Z，右方向为 -X。
            right = -delta.x * Math.cos(yaw) - delta.z * Math.sin(yaw);
            up = delta.y;
        }
        lastCameraPos = position;
        SHADOW.update(camera.getYaw(), camera.getPitch(), right, up, frameSeconds);
    }
}
