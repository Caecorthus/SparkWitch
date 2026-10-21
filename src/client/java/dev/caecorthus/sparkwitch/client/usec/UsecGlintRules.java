package dev.caecorthus.sparkwitch.client.usec;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Pure rules for the scope glint others see on a scoped USEC (S1, WP6 mockup {@code s1_glint_detail.png}). The flare
 * sits on the LPVO objective lens where the rifle model was actually drawn this pass: the lens face centre
 * {@code (8, 14, 10)} of every {@code usec_rifle*} model, taken through the item's render matrix
 * ({@link #lensOffset}, recorded by {@code UsecScopeLensAnchors}). Its intensity is a smoothstep over the
 * cosine of the angle between the USEC's aim and the direction to the viewer: full inside
 * {@link #FULL_ANGLE_DEGREES}, gone beyond {@link #ZERO_ANGLE_DEGREES}. Its size is set in screen pixels at 1080p
 * (scaled with the framebuffer height) from the target's projected scale ({@link #pixelsPerBlock}), so the Picture-in-
 * Picture lens matches Full-Screen Zoom: a core the size of the lens glass (0.04 blocks, clamped to 2.8..7 px), a flare
 * quad of 2.8 cores, and rays of 26..36 px half length, longest on a far, small target so it stays readable at 64
 * blocks while up close it stays on the optic. Presentation only.
 * 他人在开镜 USEC 身上看到的镜头反光（S1，WP6 样稿 {@code s1_glint_detail.png}）的纯规则。闪光位于本次渲染中步枪模型
 * 实际绘制出的低倍率瞄具物镜上：所有 {@code usec_rifle*} 模型的物镜镜面中心 {@code (8, 14, 10)} 经物品渲染矩阵变换
 * （{@link #lensOffset}，由 {@code UsecScopeLensAnchors} 记录）。强度是 USEC 瞄准方向与指向观察者方向夹角余弦上的 smoothstep：{@link #FULL_ANGLE_DEGREES}
 * 以内为满，超过 {@link #ZERO_ANGLE_DEGREES} 消失。大小以 1080p 屏幕像素设定（随帧缓冲高度缩放），取自目标的投影比例
 * （{@link #pixelsPerBlock}），因此画中画镜内与全画面放大一致：核心与物镜玻璃一样大（0.04 格，钳制在 2.8..7 像素），闪光
 * 四边形为 2.8 个核心，光芒半长 26..36 像素，目标越远越小时越长，因此 64 格外仍醒目，而近处仍停留在瞄具上。仅为表现。
 */
public final class UsecGlintRules {
    public static final double FULL_ANGLE_DEGREES = 6.0;
    public static final double ZERO_ANGLE_DEGREES = 35.0;
    /**
     * Objective lens face centre {@code (8, 14, 10)} of every {@code usec_rifle*} model, in the item renderer's unit
     * space after its -0.5 shift (model units / 16).
     * 所有 {@code usec_rifle*} 模型的物镜镜面中心 {@code (8, 14, 10)}，位于物品渲染器 -0.5 平移之后的单位空间（模型单位 / 16）。
     */
    public static final float LENS_MODEL_X = 8.0F / 16.0F;
    public static final float LENS_MODEL_Y = 14.0F / 16.0F;
    public static final float LENS_MODEL_Z = 10.0F / 16.0F;
    /**
     * Core radius in blocks: the objective glass (the 2.5-unit bell at about 0.035 blocks per model unit in a
     * third-person hand), so up close the white-hot core is the lens itself.
     * 核心半径（方块）：即物镜玻璃（第三人称手持时 2.5 单位的镜口，每模型单位约 0.035 格），因此近处白热核心就是镜片本身。
     */
    public static final double CORE_BLOCKS = 0.04;
    public static final double CORE_MIN_PX = 2.8;
    public static final double CORE_MAX_PX = 7.0;
    /** Flare quad half extent per core radius (the texture's core edge). / 每个核心半径对应的闪光四边形半尺寸。 */
    public static final double FLARE_PER_CORE = 2.8;
    /**
     * Ray half length: {@code RAY_FAR_PX} far away (readable at 64 blocks), easing to {@code RAY_NEAR_PX} as the target
     * grows on screen, halfway at {@code RAY_KNEE_PX_PER_BLOCK} (about 5 blocks at 1x and 70 degrees).
     * 光芒半长：远处为 {@code RAY_FAR_PX}（64 格外仍醒目），目标在屏幕上变大时缓降到 {@code RAY_NEAR_PX}，在
     * {@code RAY_KNEE_PX_PER_BLOCK}（1 倍 70 度下约 5 格）处降一半。
     */
    public static final double RAY_FAR_PX = 36.0;
    public static final double RAY_NEAR_PX = 26.0;
    public static final double RAY_KNEE_PX_PER_BLOCK = 150.0;
    /** Ray quad length : thickness, equal to its atlas cell's, so a texel stays square. / 光芒四边形长宽比，与图集格子一致。 */
    public static final double RAY_ASPECT = 8.0;
    public static final double REFERENCE_HEIGHT_PX = 1080.0;
    /** Twinkle: brightness between 90% and 100%. / 闪烁：亮度在 90% 到 100% 之间。 */
    public static final double TWINKLE_DEPTH = 0.10;
    public static final double TWINKLE_RADIANS_PER_TICK = 0.9;
    /** Twinkle: ray length between 88% and 100%, on its own beat. / 闪烁：光芒长度在 88% 到 100% 之间，节奏独立。 */
    public static final double RAY_TWINKLE_DEPTH = 0.12;
    public static final double RAY_TWINKLE_RADIANS_PER_TICK = 0.55;
    public static final int ATLAS_SIZE = 128;

    /**
     * The glint atlas ({@code usec_scope_glint.png}, made by {@code docs/plans/usec-glint/GlintTextures.java}): one
     * texture, so a look is a texture and mcmeta swap. {@link #FLARE} is the round core and halo; {@link #STREAK} the
     * screen-horizontal ray, u along it; {@link #SPIKE} the screen-vertical ray, stored lying down and drawn rotated
     * (empty in the anamorphic look). Texel rectangles of the 128 x 128 atlas.
     * 反光图集：一张贴图，因此换外观只需替换贴图与 mcmeta。{@link #FLARE} 为圆形核心与光晕；{@link #STREAK} 为屏幕水平
     * 光芒，u 沿光芒方向；{@link #SPIKE} 为屏幕竖直光芒，横放存储、旋转绘制（变形宽银幕外观中为空）。数值为 128 x 128
     * 图集中的纹素矩形。
     */
    public enum Cell {
        FLARE(0, 0, 64, 64),
        STREAK(0, 64, 128, 80),
        SPIKE(0, 80, 128, 96);

        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;

        Cell(int left, int top, int right, int bottom) {
            this.u0 = left / (float) ATLAS_SIZE;
            this.v0 = top / (float) ATLAS_SIZE;
            this.u1 = right / (float) ATLAS_SIZE;
            this.v1 = bottom / (float) ATLAS_SIZE;
        }
    }

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
     * The lens centre relative to the holder's render origin: {@code rootInverse x item x lens}. {@code item} is the
     * item matrix after the display transform, {@code rootInverse} the inverse of the matrix at the holder's render
     * HEAD, so the pass's own outer transform cancels out.
     * 物镜中心相对持有者渲染原点的位置：{@code rootInverse x item x lens}。{@code item} 为显示变换之后的物品矩阵，
     * {@code rootInverse} 为持有者渲染 HEAD 处矩阵的逆，因此本次渲染自己的外层变换会抵消。
     */
    public static Vector3f lensOffset(Matrix4fc rootInverse, Matrix4fc item, Vector3f out) {
        return rootInverse.mul(item, new Matrix4f()).transformPosition(LENS_MODEL_X, LENS_MODEL_Y, LENS_MODEL_Z, out);
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
     * Screen px per block at the lens, at 1080p: {@code pixelsPerTangent / distance / (heightPx / 1080)}, where
     * {@code pixelsPerTangent} is {@code (H / 2) / tan(fov / 2)} of the viewer's projection. Zoom raises it, so a
     * zoomed glint looks like a nearer one. A degenerate distance or projection reads as infinitely near.
     * 物镜处每格对应的 1080p 屏幕像素：{@code pixelsPerTangent / distance / (heightPx / 1080)}。放大会提高该值，因此放大后
     * 的反光看起来与更近处的一样。距离或投影退化时视为无限近。
     */
    public static double pixelsPerBlock(double distance, double pixelsPerTangent, double heightPx) {
        double perBlock = pixelsPerTangent / distance / resolutionScale(heightPx);
        return distance > 0.0 && pixelsPerTangent > 0.0 && Double.isFinite(perBlock) ? perBlock
                : Double.POSITIVE_INFINITY;
    }

    /**
     * Core radius in framebuffer px: the projected lens glass ({@link #CORE_BLOCKS}), clamped to 2.8..7 px at 1080p and
     * scaled with the height.
     * 帧缓冲像素中的核心半径：物镜玻璃（{@link #CORE_BLOCKS}）的投影，在 1080p 下钳制到 2.8..7 像素并随高度缩放。
     */
    public static double corePixels(double distance, double pixelsPerTangent, double heightPx) {
        double projected = CORE_BLOCKS * pixelsPerBlock(distance, pixelsPerTangent, heightPx);
        return Math.max(CORE_MIN_PX, Math.min(CORE_MAX_PX, projected)) * resolutionScale(heightPx);
    }

    /** Flare quad half extent in framebuffer px. / 闪光四边形半尺寸（帧缓冲像素）。 */
    public static double flareHalfPixels(double corePixels) {
        return FLARE_PER_CORE * corePixels;
    }

    /**
     * Ray quad half length in framebuffer px: {@code near + (far - near) x knee / (knee + pixelsPerBlock)}, so it stays
     * long on a far, small target and shortens as the target fills the screen.
     * 光芒四边形半长（帧缓冲像素）：远处小目标上保持较长，目标占满屏幕时变短。
     */
    public static double rayHalfLengthPixels(double distance, double pixelsPerTangent, double heightPx) {
        double perBlock = pixelsPerBlock(distance, pixelsPerTangent, heightPx);
        double far = Double.isInfinite(perBlock) ? 0.0 : RAY_KNEE_PX_PER_BLOCK / (RAY_KNEE_PX_PER_BLOCK + perBlock);
        return (RAY_NEAR_PX + (RAY_FAR_PX - RAY_NEAR_PX) * far) * resolutionScale(heightPx);
    }

    /** Ray quad half thickness in framebuffer px. / 光芒四边形半厚（帧缓冲像素）。 */
    public static double rayHalfThicknessPixels(double rayHalfLengthPixels) {
        return rayHalfLengthPixels / RAY_ASPECT;
    }

    /** Converts a screen half extent to a billboard half size in blocks at that distance. / 屏幕半尺寸换算为方块单位。 */
    public static double worldHalfSize(double halfPixels, double distance, double pixelsPerTangent) {
        if (!(pixelsPerTangent > 0.0) || !Double.isFinite(pixelsPerTangent)) {
            return 0.0;
        }
        return halfPixels * Math.max(0.0, distance) / pixelsPerTangent;
    }

    /**
     * Brightness with a subtle per-player twinkle (90%..100%). It scales the premultiplied colour only: the vertex
     * alpha stays opaque, because the core shader discards alpha below 0.1 and a fading alpha would cut a hard ring
     * into the halo.
     * 带轻微逐玩家闪烁的亮度（90%..100%）。只缩放预乘颜色：顶点透明度保持不透明，因为核心着色器会丢弃透明度低于 0.1 的
     * 片元，渐隐的透明度会在光晕上切出一道硬边。
     */
    public static double brightness(double intensity, double ageTicks, int phaseSeed) {
        double twinkle = 1.0 - TWINKLE_DEPTH * 0.5
                * (1.0 + Math.sin(ageTicks * TWINKLE_RADIANS_PER_TICK + (phaseSeed & 0xFF) * 0.37));
        return Math.max(0.0, Math.min(1.0, intensity)) * twinkle;
    }

    /** Ray length twinkle (88%..100%), on a different beat. / 光芒长度闪烁（88%..100%），节奏不同。 */
    public static double rayScale(double ageTicks, int phaseSeed) {
        return 1.0 - RAY_TWINKLE_DEPTH * 0.5
                * (1.0 + Math.sin(ageTicks * RAY_TWINKLE_RADIANS_PER_TICK + (phaseSeed & 0xFF) * 0.61));
    }

    private static double resolutionScale(double heightPx) {
        return heightPx > 0.0 && Double.isFinite(heightPx) ? heightPx / REFERENCE_HEIGHT_PX : 1.0;
    }
}
