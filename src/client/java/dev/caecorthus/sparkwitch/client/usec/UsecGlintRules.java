package dev.caecorthus.sparkwitch.client.usec;

/**
 * Pure rules for the scope glint others see on a scoped USEC (S1, WP6 mockup {@code s1_glint_detail.png}). The flare
 * sits at the scope objective (eye + 0.55 along the aim, 0.09 right, 0.06 up). Its intensity is a smoothstep over the
 * cosine of the angle between the USEC's aim and the direction to the viewer: full inside
 * {@link #FULL_ANGLE_DEGREES}, gone beyond {@link #ZERO_ANGLE_DEGREES}. Its size is set in screen pixels at 1080p
 * (scaled with the framebuffer height): a core of 0.12 blocks projected, clamped to 2.6..6 px, and a sprite half extent
 * of {@code max(34 px, 9 x core)} that carries the streaks. Presentation only.
 * 他人在开镜 USEC 身上看到的镜头反光（S1，WP6 样稿 {@code s1_glint_detail.png}）的纯规则。闪光位于物镜处（眼睛沿瞄准方向
 * 0.55、向右 0.09、向上 0.06）。强度是 USEC 瞄准方向与指向观察者方向夹角余弦上的 smoothstep：{@link #FULL_ANGLE_DEGREES}
 * 以内为满，超过 {@link #ZERO_ANGLE_DEGREES} 消失。大小以 1080p 屏幕像素设定（随帧缓冲高度缩放）：核心为 0.12 格的投影，
 * 钳制在 2.6..6 像素，精灵半尺寸为 {@code max(34 像素, 9 x 核心)}，承载光芒。仅为表现。
 */
public final class UsecGlintRules {
    public static final double FULL_ANGLE_DEGREES = 6.0;
    public static final double ZERO_ANGLE_DEGREES = 35.0;
    public static final double SCOPE_FORWARD = 0.55;
    public static final double SCOPE_RIGHT = 0.09;
    public static final double SCOPE_UP = 0.06;
    public static final double CORE_BLOCKS = 0.12;
    public static final double CORE_MIN_PX = 2.6;
    public static final double CORE_MAX_PX = 6.0;
    public static final double STREAK_MIN_PX = 34.0;
    public static final double STREAK_PER_CORE = 9.0;
    public static final double REFERENCE_HEIGHT_PX = 1080.0;
    /** Optional twinkle: alpha between 90% and 100%. / 可选闪烁：透明度在 90% 到 100% 之间。 */
    public static final double TWINKLE_DEPTH = 0.10;
    public static final double TWINKLE_RADIANS_PER_TICK = 0.9;

    private static final double COS_FULL = Math.cos(Math.toRadians(FULL_ANGLE_DEGREES));
    private static final double COS_ZERO = Math.cos(Math.toRadians(ZERO_ANGLE_DEGREES));

    private UsecGlintRules() {
    }

    /**
     * Who draws a glint for a scoped player: a confirmed server, no Blind view on this client (its world is echo
     * line art, so a light would leak), never the viewer's own player or the entity the camera rides, never an
     * invisible or spectating player.
     * 谁会为开镜玩家绘制反光：已确认的服务端、本客户端没有盲人视图（其世界是回声线稿，光点会泄露信息）、不是观察者自己或
     * 镜头所附的实体，也不是隐身或旁观的玩家。
     */
    public static boolean draws(boolean confirmedServer, boolean blindView, boolean scoped, boolean localPlayer,
                                boolean cameraEntity, boolean invisible, boolean spectator) {
        return confirmedServer && !blindView && scoped && !localPlayer && !cameraEntity && !invisible && !spectator;
    }

    /**
     * Cosine of the angle between the aim and the vector to the viewer; NaN when either vector is degenerate.
     * 瞄准方向与指向观察者向量夹角的余弦；任一向量退化时为 NaN。
     */
    public static double aimCosine(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double toLength = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (!(lookLength > 1.0E-9) || !(toLength > 1.0E-9)) {
            return Double.NaN;
        }
        return Math.max(-1.0, Math.min(1.0, (lookX * toX + lookY * toY + lookZ * toZ) / (lookLength * toLength)));
    }

    /**
     * Smoothstep from {@link #ZERO_ANGLE_DEGREES} to {@link #FULL_ANGLE_DEGREES} in cosine space; NaN reads as 0.
     * 在余弦空间中从 {@link #ZERO_ANGLE_DEGREES} 到 {@link #FULL_ANGLE_DEGREES} 的 smoothstep；NaN 视为 0。
     */
    public static double intensity(double aimCosine) {
        if (Double.isNaN(aimCosine)) {
            return 0.0;
        }
        double t = Math.max(0.0, Math.min(1.0, (aimCosine - COS_ZERO) / (COS_FULL - COS_ZERO)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Intensity for an angle in degrees (tests and tuning). / 按角度（度）计算的强度（测试与调参）。 */
    public static double intensityAtDegrees(double angleDegrees) {
        return intensity(Math.cos(Math.toRadians(angleDegrees)));
    }

    /**
     * Core radius in framebuffer px: 0.12 blocks projected, clamped to 2.6..6 px at 1080p and scaled with the height.
     * {@code pixelsPerTangent} is {@code (H / 2) / tan(fov / 2)} of the viewer's projection.
     * 帧缓冲像素中的核心半径：0.12 格的投影，在 1080p 下钳制到 2.6..6 像素并随高度缩放。
     */
    public static double corePixels(double distance, double pixelsPerTangent, double heightPx) {
        double scale = resolutionScale(heightPx);
        double projected = distance > 0.0 && pixelsPerTangent > 0.0
                ? CORE_BLOCKS * pixelsPerTangent / distance / scale : CORE_MAX_PX;
        double clamped = Double.isFinite(projected) ? Math.max(CORE_MIN_PX, Math.min(CORE_MAX_PX, projected))
                : CORE_MAX_PX;
        return clamped * scale;
    }

    /** Sprite half extent in framebuffer px: {@code max(34 px, 9 x core)}. / 精灵半尺寸（帧缓冲像素）。 */
    public static double spriteHalfPixels(double corePixels, double heightPx) {
        return Math.max(STREAK_MIN_PX * resolutionScale(heightPx), STREAK_PER_CORE * corePixels);
    }

    /** Converts a screen half extent to a billboard half size in blocks at that distance. / 屏幕半尺寸换算为方块单位。 */
    public static double worldHalfSize(double halfPixels, double distance, double pixelsPerTangent) {
        if (!(pixelsPerTangent > 0.0) || !Double.isFinite(pixelsPerTangent)) {
            return 0.0;
        }
        return halfPixels * Math.max(0.0, distance) / pixelsPerTangent;
    }

    /** Alpha with a subtle per-player twinkle (90%..100%). / 带轻微逐玩家闪烁的透明度（90%..100%）。 */
    public static double alpha(double intensity, double ageTicks, int phaseSeed) {
        double twinkle = 1.0 - TWINKLE_DEPTH * 0.5
                * (1.0 + Math.sin(ageTicks * TWINKLE_RADIANS_PER_TICK + (phaseSeed & 0xFF) * 0.37));
        return Math.max(0.0, Math.min(1.0, intensity)) * twinkle;
    }

    private static double resolutionScale(double heightPx) {
        return heightPx > 0.0 && Double.isFinite(heightPx) ? heightPx / REFERENCE_HEIGHT_PX : 1.0;
    }
}
