package dev.caecorthus.sparkwitch.client.scope;

/**
 * Pure rules of the scope module (no Minecraft state), unit-tested. Presentation only.
 * 开镜模块的纯规则（不读取 Minecraft 状态），有单元测试。仅用于展示。
 */
public final class ScopeRules {
    /**
     * Whether the PiP renderer ({@link ScopePictureInPicture}, WP4b) may run at all; while false, PICTURE_IN_PICTURE
     * renders as ZOOM_BLUR.
     * 画中画渲染器（{@link ScopePictureInPicture}，WP4b）是否允许运行；为 false 时画中画按全画面放大渲染。
     */
    public static final boolean PICTURE_IN_PICTURE_AVAILABLE = true;
    /**
     * The PiP lens render covers this many lens radii around the centre, so the barrel distortion's rim samples
     * (1 + 0.06 at the edge) and the edge anti-aliasing stay inside it.
     * 画中画镜内渲染覆盖镜片中心周围这么多个镜片半径，使桶形畸变在镜缘的采样（边缘处为 1 + 0.06）与边缘抗锯齿都落在其中。
     */
    public static final float LENS_VIEW_MARGIN = 1.1F;
    /**
     * Longest side of the square PiP lens render in pixels (owner brief: a reduced lens resolution, about 512²); a
     * smaller lens renders at its own size.
     * 画中画方形镜内渲染的最长边（像素；所有者要求：降低的镜内分辨率，约 512²）；更小的镜片按自身尺寸渲染。
     */
    public static final int LENS_VIEW_MAX_PIXELS = 512;
    public static final int LENS_VIEW_MIN_PIXELS = 64;
    /**
     * Vanilla closes half of the FOV gap every tick (50 ms); the PiP lens zoom eases per frame with the same half-life.
     * 原版每 tick（50 ms）缩小一半的视场差距；画中画镜内放大按相同半衰期逐帧缓动。
     */
    public static final double ZOOM_EASE_HALF_LIFE_SECONDS = 0.05;
    /** Main-view FOV range the PiP projection accepts, degrees. / 画中画投影接受的主画面视场角范围（度）。 */
    public static final double MIN_PROJECTION_FOV_DEGREES = 1.0;
    public static final double MAX_PROJECTION_FOV_DEGREES = 170.0;
    /** The lens fades in over this long after scoping in. / 开镜后镜片效果在此时长内淡入。 */
    public static final long LENS_FADE_NANOS = 120_000_000L;
    /**
     * The private post processor is kept this long after scoping out, so a quick re-scope does not rebuild it.
     * 退出开镜后私有后处理器再保留这么久，快速再次开镜时无需重建。
     */
    public static final long RELEASE_AFTER_IDLE_NANOS = 2_000_000_000L;
    public static final int FIRST_RETRY_SECONDS = 2;
    public static final int MAX_RETRY_SECONDS = 30;
    /** GameRenderer clamps the eased FOV multiplier at 0.1; deeper requests gain nothing. / 原版把缓动后的倍率钳制在 0.1。 */
    public static final float MIN_FOV_MULTIPLIER = 0.1F;
    public static final float MAX_SENSITIVITY_MULTIPLIER = 4.0F;

    private ScopeRules() {
    }

    /**
     * The generic view gate checked before any provider is asked: a local player, first person, the camera is that
     * player, no screen is open, not a spectator.
     * 在询问任何提供者之前检查的通用视角条件：存在本地玩家、第一人称、相机即该玩家、未打开界面、不是旁观者。
     */
    public static boolean viewAllowsScope(boolean hasPlayer, boolean firstPerson, boolean cameraIsPlayer,
                                          boolean screenOpen, boolean spectator) {
        return hasPlayer && firstPerson && cameraIsPlayer && !screenOpen && !spectator;
    }

    /**
     * The mode that actually renders. PiP needs: the player's choice, no shader pack (ZOOM_BLUR then uses its HUD
     * fallback), no Fabulous! graphics (its transparency post pass composites into the real main framebuffer, so a
     * second world render would lose water, glass and particles), no exclusive full-frame view that captures the world
     * render once per frame (the Blind echo view), and a ready PiP renderer (available, not backing off after a
     * failure). Anything else renders as ZOOM_BLUR.
     * 实际渲染的模式。画中画需要：玩家选择了它、未开光影包（此时全画面放大使用 HUD 回退）、未使用「极佳」画质（其透明度后处理
     * 会合成到真正的主帧缓冲，二次世界渲染会丢失水、玻璃与粒子）、没有每帧只捕获一次世界渲染的独占全画面视图（盲人回声视图），
     * 且画中画渲染器就绪（已开放、未处于失败退避中）。其余情况均按全画面放大渲染。
     */
    public static ScopeMode effectiveMode(ScopeMode selected, boolean shaderPackInUse, boolean fabulousGraphics,
                                          boolean exclusiveViewActive, boolean pictureInPictureReady) {
        if (selected == ScopeMode.PICTURE_IN_PICTURE && !shaderPackInUse && !fabulousGraphics && !exclusiveViewActive
                && pictureInPictureReady) {
            return ScopeMode.PICTURE_IN_PICTURE;
        }
        return ScopeMode.ZOOM_BLUR;
    }

    /** A profile's FOV multiplier made safe: (0.1..1], anything unusable means no zoom. / 安全化的 FOV 乘数。 */
    public static float zoomFactor(float profileMultiplier) {
        if (!Float.isFinite(profileMultiplier) || profileMultiplier <= 0.0F) {
            return 1.0F;
        }
        return Math.max(MIN_FOV_MULTIPLIER, Math.min(1.0F, profileMultiplier));
    }

    /**
     * Mouse-look scale while scoped: the profile's base multiplier times the player's percentage.
     * 开镜时的鼠标视角缩放：配置的基础倍率乘以玩家设置的百分比。
     */
    public static double lookScale(float profileMultiplier, int sensitivityPercent) {
        double base = Float.isFinite(profileMultiplier)
                ? Math.max(0.0, Math.min(MAX_SENSITIVITY_MULTIPLIER, profileMultiplier))
                : 1.0;
        return base * ScopeSettings.clampPercent(sensitivityPercent) / 100.0;
    }

    /**
     * One frame of the PiP lens zoom easing toward {@code target} (vanilla's FOV curve, per frame): half the gap per
     * {@link #ZOOM_EASE_HALF_LIFE_SECONDS}. A non-finite or non-positive frame time keeps {@code current}.
     * 画中画镜内放大向 {@code target} 缓动一帧（逐帧版的原版视场曲线）：每 {@link #ZOOM_EASE_HALF_LIFE_SECONDS} 缩小一半差距。
     * 帧时间非有限或非正时保持 {@code current}。
     */
    public static float easeZoom(float current, float target, double frameSeconds) {
        if (!Float.isFinite(current)) {
            return target;
        }
        if (!(frameSeconds > 0.0) || !Double.isFinite(frameSeconds)) {
            return current;
        }
        return (float) (target + (current - target) * Math.pow(0.5, frameSeconds / ZOOM_EASE_HALF_LIFE_SECONDS));
    }

    /**
     * Side of the square PiP lens render: the lens diameter plus {@link #LENS_VIEW_MARGIN}, in framebuffer pixels,
     * within [{@link #LENS_VIEW_MIN_PIXELS}, {@link #LENS_VIEW_MAX_PIXELS}] and never above the framebuffer's short side
     * (the entity-outline composite maps the lens render 1:1 onto the corner of the window-sized outline target).
     * 画中画方形镜内渲染的边长：镜片直径加 {@link #LENS_VIEW_MARGIN}（帧缓冲像素），限制在给定范围内，且绝不超过帧缓冲短边
     * （实体描边合成把镜内渲染 1:1 映射到窗口尺寸描边目标的一角）。
     */
    public static int lensViewSize(int framebufferWidth, int framebufferHeight) {
        int shortSide = Math.min(framebufferWidth, framebufferHeight);
        if (shortSide <= 0) {
            return LENS_VIEW_MIN_PIXELS;
        }
        double diameter = 2.0 * LENS_VIEW_MARGIN * ScopeLensGeometry.lensRadius(framebufferWidth, framebufferHeight);
        int side = (int) Math.ceil(diameter);
        return Math.max(1, Math.min(shortSide, Math.max(LENS_VIEW_MIN_PIXELS, Math.min(LENS_VIEW_MAX_PIXELS, side))));
    }

    /**
     * Clip-space scale along one screen axis that turns this frame's main projection into the PiP lens projection
     * (lens = diag(sx, sy, 1, 1) x main): the main FOV narrowed by {@code zoom} (exactly what ZOOM_BLUR would show),
     * then the square spanning {@link #LENS_VIEW_MARGIN} lens radii stretched over the whole lens target. Near/far,
     * bobbing, hurt tilt, recoil and nausea in the main projection carry over unchanged.
     * 沿一个屏幕轴的裁剪空间缩放，把本帧主投影变为画中画镜内投影（镜内 = diag(sx, sy, 1, 1) x 主投影）：主视场按 {@code zoom}
     * 收窄（与全画面放大所见完全一致），再把覆盖 {@link #LENS_VIEW_MARGIN} 个镜片半径的正方形拉伸到整个镜内目标。主投影中的
     * 近远平面、视角摇晃、受伤倾斜、后坐力与反胃效果原样保留。
     *
     * @param mainFovDegrees vertical FOV of the main projection / 主投影的竖直视场角
     * @param zoom           lens FOV multiplier, (0, 1] / 镜内视场倍率
     * @param axisPixels     framebuffer width (x) or height (y) / 帧缓冲宽（x）或高（y）
     * @param lensRadius     lens radius in framebuffer pixels / 镜片半径（帧缓冲像素）
     */
    public static float lensClipScale(double mainFovDegrees, float zoom, double axisPixels, double lensRadius) {
        double fov = clampFov(mainFovDegrees);
        double zoomed = clampFov(fov * zoomFactor(zoom));
        double narrowing = Math.tan(Math.toRadians(fov) / 2.0) / Math.tan(Math.toRadians(zoomed) / 2.0);
        return (float) (narrowing * axisPixels / (2.0 * LENS_VIEW_MARGIN * Math.max(lensRadius, 1.0)));
    }

    /**
     * The vertical FOV a full-screen projection would need to show the PiP lens at its on-screen scale: the reticle
     * converts angles to lens pixels with it, exactly as in ZOOM_BLUR.
     * 全屏投影要以画中画镜片的屏幕比例显示时所需的竖直视场角：分划用它把角度换算为镜内像素，与全画面放大完全一致。
     */
    public static double equivalentFovDegrees(double mainFovDegrees, float zoom) {
        return clampFov(clampFov(mainFovDegrees) * zoomFactor(zoom));
    }

    private static double clampFov(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 70.0;
        }
        return Math.max(MIN_PROJECTION_FOV_DEGREES, Math.min(MAX_PROJECTION_FOV_DEGREES, degrees));
    }

    /** Lens fade-in (0..1) after scoping in. / 开镜后的镜片淡入系数（0..1）。 */
    public static float lensFade(long scopedNanos) {
        if (scopedNanos <= 0L) {
            return 0.0F;
        }
        return (float) Math.min(1.0, (double) scopedNanos / LENS_FADE_NANOS);
    }

    /** Whether an idle processor should be closed. / 空闲的处理器是否应关闭。 */
    public static boolean releasesProcessor(long idleNanos) {
        return idleNanos >= RELEASE_AFTER_IDLE_NANOS;
    }

    /**
     * Seconds to wait before rebuilding a failed lens pipeline: 2, 4, 8, 16, then 30 (the Blind echo-view schedule). A
     * one-off error recovers quickly; a persistent one retries rarely, and the HUD fallback ring shows meanwhile.
     * 重建失败的镜片管线前等待的秒数：2、4、8、16，之后为 30（与盲人回声画面相同）。偶发错误很快恢复；持续错误
     * 很少重试，期间显示 HUD 回退环。
     */
    public static int retryDelaySeconds(int failures) {
        int doublings = Math.min(Math.max(failures, 1) - 1, 5);
        return Math.min(FIRST_RETRY_SECONDS << doublings, MAX_RETRY_SECONDS);
    }
}
