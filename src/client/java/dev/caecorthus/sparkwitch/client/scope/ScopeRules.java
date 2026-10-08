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
     * Hard cap on the side of the square PiP lens render, in pixels, whatever the player's Lens Resolution. 2048 is the
     * smallest power of two above the native lens of 4K UHD (3840x2160: 1996 px), so every screen up to 4K renders the
     * lens natively at 100 %; 5K and larger stay at 2048² (4.2 MP, about the 4K cost, 32 MiB with depth) instead of
     * growing to 2661² and beyond. It is always below the main framebuffer's short side when it applies.
     * 画中画方形镜内渲染边长的硬上限（像素），与玩家的镜内分辨率设置无关。2048 是大于 4K UHD 原生镜片（3840x2160：1996 像素）的
     * 最小 2 的幂，因此 4K 及以下的屏幕在 100 % 时都按原生尺寸渲染镜内画面；5K 及更大的屏幕停在 2048²（420 万像素，约等于
     * 4K 的开销，含深度 32 MiB），不会增长到 2661² 以上。该上限生效时总小于主帧缓冲的短边。
     */
    public static final int LENS_VIEW_MAX_PIXELS = 2048;
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

    /**
     * Whether a lens composite barrel-distorts the picture of a frame rendered in {@code mode}: always in
     * PICTURE_IN_PICTURE, and in ZOOM_BLUR unless its HUD fallback ring stands in for the filter (a shader pack or a
     * failing pipeline, {@code ScopeLensFilter.fallbackActive()}), i.e. exactly when {@code ScopeHud} draws no
     * fallback ring. A reticle that corrects for {@link ScopeLensGeometry#BARREL_DISTORTION} does so only then.
     * 以 {@code mode} 渲染的一帧的镜内画面是否被镜片合成着色器做了桶形畸变：画中画时总是；全画面放大时，除非由 HUD 回退环
     * 代替滤镜（光影包或管线失败，{@code ScopeLensFilter.fallbackActive()}），即恰在 {@code ScopeHud} 不画回退环时。
     * 校正 {@link ScopeLensGeometry#BARREL_DISTORTION} 的分划只应在此时校正。
     */
    public static boolean lensDistorts(ScopeMode mode, boolean zoomBlurFallbackActive) {
        return mode == ScopeMode.PICTURE_IN_PICTURE || (mode == ScopeMode.ZOOM_BLUR && !zoomBlurFallbackActive);
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
     * The wheel delta {@code Mouse#onMouseScroll} accumulates for the hotbar, recomputed from the raw GLFW vertical
     * offset: its sign only under Discrete Scrolling, times Mouse Wheel Sensitivity. Trackpads and high-resolution
     * wheels give fractions; 0 for a non-finite input.
     * {@code Mouse#onMouseScroll} 为快捷栏累加的滚轮增量，由 GLFW 原始竖直偏移重新计算：「离散滚动」时只取符号，再乘以
     * 「滚轮灵敏度」。触控板与高精度滚轮会给出小数；非有限输入为 0。
     */
    public static double wheelNotches(double vertical, boolean discreteScroll, double wheelSensitivity) {
        if (!Double.isFinite(vertical) || !Double.isFinite(wheelSensitivity)) {
            return 0.0;
        }
        return (discreteScroll ? Math.signum(vertical) : vertical) * wheelSensitivity;
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
     * The native PiP lens render side, in framebuffer pixels: the on-screen span the composite stretches the whole lens
     * target over, 2 x {@link #LENS_VIEW_MARGIN} lens radii, so a target this size has one texel per screen pixel.
     * 画中画镜内渲染的原生边长（帧缓冲像素）：合成时整个镜内目标在屏幕上覆盖的跨度，即 2 x {@link #LENS_VIEW_MARGIN} 个镜片
     * 半径；该尺寸的目标每个纹素对应一个屏幕像素。
     */
    public static double nativeLensViewPixels(int framebufferWidth, int framebufferHeight) {
        return 2.0 * LENS_VIEW_MARGIN * ScopeLensGeometry.lensRadius(framebufferWidth, framebufferHeight);
    }

    /**
     * Side of the square PiP lens render for the player's Lens Resolution: {@code resolutionPercent} (one of
     * {@link ScopeSettings#LENS_RESOLUTION_STEPS}; anything else means 100 %) of {@link #nativeLensViewPixels}, rounded
     * to the nearest whole pixel with the same parity as the framebuffer's short side, then kept within
     * [{@link #LENS_VIEW_MIN_PIXELS}, {@link #LENS_VIEW_MAX_PIXELS}] and never above the short side (the entity-outline
     * composite maps the lens render 1:1 onto the corner of the window-sized outline target). It depends on the
     * framebuffer size only, never on the GUI scale. The parity matters at 100 %: the composite samples the lens with
     * linear filtering, and with a matching parity the texels sit on screen-pixel centres around the lens centre, so a
     * native lens is as crisp as Full-Screen Zoom there instead of a half-texel blend; smaller steps upscale smoothly.
     * 玩家镜内分辨率对应的画中画方形镜内渲染边长：{@link #nativeLensViewPixels} 的 {@code resolutionPercent}（属于
     * {@link ScopeSettings#LENS_RESOLUTION_STEPS}；其他值按 100 % 处理），取与帧缓冲短边奇偶性相同的最近整数像素，再限制在
     * [{@link #LENS_VIEW_MIN_PIXELS}, {@link #LENS_VIEW_MAX_PIXELS}] 内且绝不超过短边（实体描边合成把镜内渲染 1:1 映射到窗口
     * 尺寸描边目标的一角）。它只取决于帧缓冲尺寸，与 GUI 缩放无关。奇偶性在 100 % 时有意义：合成以线性过滤采样镜内画面，
     * 奇偶一致时镜片中心附近的纹素正好落在屏幕像素中心，原生镜内画面在那里与全画面放大一样清晰，而不是半个纹素的混合；
     * 较低档位则平滑放大。
     */
    public static int lensViewSize(int framebufferWidth, int framebufferHeight, int resolutionPercent) {
        int shortSide = Math.min(framebufferWidth, framebufferHeight);
        if (shortSide <= 0) {
            return LENS_VIEW_MIN_PIXELS;
        }
        double wanted = nativeLensViewPixels(framebufferWidth, framebufferHeight)
                * ScopeSettings.lensResolutionOrDefault(resolutionPercent) / 100.0;
        int parity = shortSide & 1;
        long side = Math.round((wanted - parity) / 2.0) * 2L + parity;
        return (int) Math.max(1, Math.min(shortSide, Math.max(LENS_VIEW_MIN_PIXELS,
                Math.min(LENS_VIEW_MAX_PIXELS, side))));
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

    /**
     * On-screen (main framebuffer) pixels spanned by one unit of vertical NDC in a world pass, for world-render hooks
     * that size sprites in screen pixels. The main pass fills the screen: half its height. The PiP lens pass renders a
     * square target whose whole [-1, 1] NDC range the composite stretches over 2 x {@link #LENS_VIEW_MARGIN} lens
     * radii: {@link #LENS_VIEW_MARGIN} x the lens radius, whatever the target's own size ({@link #lensViewSize}, which
     * follows the player's Lens Resolution). Times the pass's projection m11 this gives on-screen pixels per unit
     * tangent, and in the lens that product equals ZOOM_BLUR's at the same zoom ({@link #lensClipScale} uses the same
     * radius), so a sprite sized with it looks the same in both modes.
     * 世界渲染中竖直 NDC 一个单位在屏幕（主帧缓冲）上覆盖的像素数，供以屏幕像素设定大小的世界渲染钩子使用。主渲染铺满屏幕：
     * 屏幕高度的一半。画中画镜内渲染输出一个方形目标，合成时把它整个 [-1, 1] NDC 范围拉伸到 2 x {@link #LENS_VIEW_MARGIN}
     * 个镜片半径上：即 {@link #LENS_VIEW_MARGIN} x 镜片半径，与目标自身尺寸（{@link #lensViewSize}，随玩家的镜内分辨率变化）
     * 无关。乘以该次渲染投影的 m11 即为每单位正切的屏幕像素；镜内该乘积与相同倍率下全画面放大的值相等
     * （{@link #lensClipScale} 使用同一半径），因此据此设定大小的精灵在两种模式下看起来一样大。
     *
     * @param lensPass     whether the PiP lens pass is rendering ({@code ScopeClient.isRenderingLens()}) / 是否处于镜内渲染
     * @param screenWidth  main framebuffer width in pixels / 主帧缓冲宽度（像素）
     * @param screenHeight main framebuffer height in pixels / 主帧缓冲高度（像素）
     */
    public static double screenPixelsPerNdcY(boolean lensPass, double screenWidth, double screenHeight) {
        if (!lensPass) {
            return Math.max(0.0, screenHeight) / 2.0;
        }
        return LENS_VIEW_MARGIN * Math.max(ScopeLensGeometry.lensRadius(screenWidth, screenHeight), 1.0);
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
