package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

/**
 * Client only. The scope module's per-frame runtime behind the {@code client/mixin/scope} hooks: zoom, look scale,
 * hand hiding, and the once-per-frame update (scope shadow, lens fade, lens filter) that runs right before the GUI
 * pass. Render thread only; nothing is synced or saved.
 * 仅客户端。{@code client/mixin/scope} 钩子背后的开镜模块逐帧运行时：放大、视角缩放、隐藏手，以及紧接在 GUI 之前、
 * 每帧一次的更新（镜内阴影、镜片淡入、镜片滤镜）。仅渲染线程；不同步也不保存任何内容。
 */
public final class ScopeRuntime {
    private static final long NOT_SCOPED = Long.MIN_VALUE;
    private static final ScopeShadow SHADOW = new ScopeShadow();
    private static long scopedSinceNanos = NOT_SCOPED;
    private static long lastScopedNanos;
    private static long lastFrameNanos;
    private static Vec3d lastCameraPos;

    private ScopeRuntime() {
    }

    /**
     * {@code AbstractClientPlayerEntity#getFovMultiplier} RETURN: zooms only the local player while scoped.
     * 仅在开镜时放大本地玩家的视场。
     */
    public static float fovMultiplier(AbstractClientPlayerEntity player, float original) {
        if (player != MinecraftClient.getInstance().player) {
            return original;
        }
        ScopeProfile profile = ScopeClient.activeProfile();
        return profile == null ? original : original * ScopeRules.zoomFactor(profile.fovMultiplier());
    }

    /** Mouse-look scale for {@code Mouse#updateMouse}; 1 when unscoped. / 鼠标视角缩放；未开镜时为 1。 */
    public static double lookScale() {
        ScopeProfile profile = ScopeClient.activeProfile();
        return profile == null ? 1.0
                : ScopeRules.lookScale(profile.sensitivityMultiplier(),
                ScopeSettingsStore.current().sensitivityPercent());
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
     * {@code GameRenderer#render}, right before the GUI binds the main framebuffer: updates the shadow and fade, runs
     * the lens filter while scoped, and releases it after an idle grace period otherwise.
     * {@code GameRenderer#render} 中紧接在 GUI 绑定主帧缓冲之前：更新阴影与淡入，开镜时运行镜片滤镜，否则在空闲宽限期后释放。
     */
    public static void beforeGui(RenderTickCounter tickCounter) {
        long now = Util.getMeasuringTimeNano();
        double frameSeconds = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) / 1.0E9;
        lastFrameNanos = now;
        MinecraftClient client = MinecraftClient.getInstance();
        ScopeProfile profile = ScopeClient.activeProfile();
        if (profile == null) {
            if (scopedSinceNanos != NOT_SCOPED) {
                scopedSinceNanos = NOT_SCOPED;
                SHADOW.reset();
                lastCameraPos = null;
            }
            ScopeLensFilter.idle(now - lastScopedNanos);
            return;
        }
        if (scopedSinceNanos == NOT_SCOPED) {
            scopedSinceNanos = now;
        }
        lastScopedNanos = now;
        updateShadow(client.gameRenderer.getCamera(), frameSeconds);
        ScopeLensFilter.render(client, ScopeRules.lensFade(now - scopedSinceNanos), SHADOW.x(), SHADOW.y(),
                tickCounter.getLastFrameDuration());
    }

    /**
     * Disconnect, login start, client stop: forget the frame state and close the lens filter (deferred to the render
     * thread when called elsewhere).
     * 断线、开始登录、客户端停止时：清除逐帧状态并关闭镜片滤镜（在其他线程调用时推迟到渲染线程）。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ScopeRuntime::reset);
            return;
        }
        scopedSinceNanos = NOT_SCOPED;
        lastScopedNanos = 0L;
        lastFrameNanos = 0L;
        lastCameraPos = null;
        SHADOW.reset();
        ScopeLensFilter.reset();
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
