package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure rules of the visible BEAR smoke: the ray-march constants the GLSL receives as {@code #define}s (one source of
 * truth, see {@link #shaderDefines()}), and Java mirrors of the shader's ray-box, step, erosion and upsample math.
 * The CPU uses the culling helpers each frame; the mirrors document and pin what the shader does.
 * Noise erosion only ever removes density and keeps density 1 at 1, so an outline mask that marches the raw density
 * with {@link SmokeVolumes#EXTINCTION_PER_BLOCK} is never weaker than the smoke the player sees.
 * BEAR 可见烟雾的纯规则：GLSL 以 {@code #define} 形式接收的光线步进常量（唯一来源，见 {@link #shaderDefines()}），以及
 * 着色器中光线-盒子、步长、侵蚀与放大算法的 Java 镜像。CPU 每帧使用裁剪辅助函数；镜像用于说明并固定着色器行为。
 * 噪声侵蚀只会减少浓度且浓度 1 保持为 1，因此用 {@link SmokeVolumes#EXTINCTION_PER_BLOCK} 步进原始浓度的描边遮罩
 * 绝不会比玩家看到的烟更弱。
 */
public final class SmokeMarchRules {
    /** Clouds marched per pixel; farther clouds are dropped. / 每像素步进的烟团数；更远的被舍弃。 */
    public static final int MAX_CLOUDS = 8;
    /** Hard cap on march steps per half-resolution pixel. / 每个半分辨率像素的步数上限。 */
    public static final int MAX_STEPS = 48;
    /** Step near the eye, in blocks (optical depth 0.375 per step in full smoke). / 眼前的步长（格；满浓度时每步光学厚度 0.375）。 */
    public static final float MIN_STEP = 0.25F;
    /** Extra step length per block of eye distance. / 每格视距增加的步长。 */
    public static final float STEP_GROWTH = 0.015F;
    /** Largest distance-grown step before the step budget takes over. / 步数预算接管之前，随距离增长的最大步长。 */
    public static final float NEAR_STEP_CAP = 1.0F;
    /** A step after an empty sample is this much longer. / 空样本之后的一步加长倍数。 */
    public static final float EMPTY_STEP_SCALE = 2.0F;
    public static final float EMPTY_DENSITY = 0.002F;
    /**
     * Raw density from which a sample counts as full smoke and skips the noise (erosion keeps 1 at 1 anyway).
     * 原始浓度达到此值即视为满浓度并跳过噪声（侵蚀本就保持 1 为 1）。
     */
    public static final float FULL_DENSITY = 0.99F;
    /**
     * Below this transmittance the self-shadow tap is no longer refreshed (later samples are mostly hidden).
     * 透射率低于此值后不再刷新自阴影采样（之后的样本大多已被遮住）。
     */
    public static final float SHADOW_MIN_TRANSMITTANCE = 0.2F;
    /** Transmittance at which the march stops and counts the rest as opaque. / 步进停止并视余量为不透明的透射率。 */
    public static final float OPAQUE_TRANSMITTANCE = 0.02F;
    /**
     * Steps lengthen as the view fills up: at transmittance T a step is {@code 1 + this * (1 - T)} times longer, since
     * what lies behind contributes at most T.
     * 视线被填满时步长随之变长：透射率为 T 时步长乘以 {@code 1 + 本值 * (1 - T)}，因为其后的内容最多贡献 T。
     */
    public static final float OPACITY_STEP_GROWTH = 1.0F;
    /** Composite skips pixels at least this clear. / 合成跳过透射率不低于此值的像素。 */
    public static final float CLEAR_TRANSMITTANCE = 0.998F;
    /** Low-frequency (shape) erosion strength. / 低频（形状）侵蚀强度。 */
    public static final float EROSION_SHAPE = 0.6F;
    /** High-frequency (detail) erosion strength. / 高频（细节）侵蚀强度。 */
    public static final float EROSION_DETAIL = 0.35F;
    /** Largest domain-warp offset of a density sample, in blocks. / 浓度采样的最大域扭曲偏移（格）。 */
    public static final float WARP_BLOCKS = 0.35F;
    /** World size of one noise tile, in blocks. / 一块噪声平铺的世界尺寸（格）。 */
    public static final float NOISE_PERIOD_BLOCKS = 6.0F;
    /** Detail octave frequency relative to the shape octave. / 细节倍频相对形状倍频的频率。 */
    public static final float DETAIL_SCALE = 2.71F;
    /** Height of the self-shadow tap above each sample, in blocks. / 自阴影采样点在样本上方的高度（格）。 */
    public static final float SHADOW_DISTANCE = 0.75F;
    public static final float SHADOW_STRENGTH = 1.0F;
    /** Share of the light that reaches fully shadowed smoke. / 完全处于阴影中的烟仍能得到的光照比例。 */
    public static final float AMBIENT_SHARE = 0.55F;
    /**
     * Share of the shape erosion that swings with the edge breathing ({@link SmokeMotion}).
     * 形状侵蚀中随边缘呼吸摆动的比例（{@link SmokeMotion}）。
     */
    public static final float BREATHE_DEPTH = 0.4F;
    /** How much the advected noise varies the self-shadow density. / 平流噪声对自阴影浓度的调制幅度。 */
    public static final float BILLOW_SHADE = 0.8F;
    /** How much the advected noise varies the brightness. / 平流噪声对亮度的调制幅度。 */
    public static final float CHURN_SHADE = 0.15F;
    /** White-grey smoke albedo (slightly cool). / 白灰色烟雾反照率（略偏冷）。 */
    public static final float ALBEDO_R = 0.86F;
    public static final float ALBEDO_G = 0.87F;
    public static final float ALBEDO_B = 0.90F;
    /** Relative depth difference that halves an upsample tap's weight. / 使放大采样权重减半的相对深度差。 */
    public static final float UPSAMPLE_DEPTH_EPSILON = 0.05F;
    /**
     * Relative depth spread below which all four taps lie on one surface and the composite uses one hardware bilinear
     * fetch (where {@link #upsampleWeights} is bilinear within a few percent anyway).
     * 相对深度差低于此值时四个采样位于同一表面，合成只做一次硬件双线性采样（此时 {@link #upsampleWeights} 本就与双线性
     * 相差无几）。
     */
    public static final float UPSAMPLE_FLAT = 0.02F;
    /** Keeps a texel that sits exactly on a bilinear zero selectable by depth. / 让恰好处于双线性零权重位置的纹素仍可按深度被选中。 */
    public static final float UPSAMPLE_BILINEAR_FLOOR = 0.001F;
    /** Interleaved-gradient-noise frame offset (Jimenez 2014). / 交错梯度噪声逐帧偏移（Jimenez 2014）。 */
    public static final float JITTER_FRAME_OFFSET = 5.588238F;
    /** Padding of the scissor rectangle, in full-resolution pixels. / 裁剪矩形的外扩（全分辨率像素）。 */
    public static final int SCREEN_RECT_PADDING = 4;

    private SmokeMarchRules() {
    }

    /** The {@code #define}s every smoke program is compiled with. / 所有烟雾程序编译时注入的 {@code #define}。 */
    public static Map<String, String> shaderDefines() {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("MAX_CLOUDS", Integer.toString(MAX_CLOUDS));
        defines.put("MAX_STEPS", Integer.toString(MAX_STEPS));
        defines.put("MIN_STEP", glsl(MIN_STEP));
        defines.put("STEP_GROWTH", glsl(STEP_GROWTH));
        defines.put("NEAR_STEP_CAP", glsl(NEAR_STEP_CAP));
        defines.put("EMPTY_STEP_SCALE", glsl(EMPTY_STEP_SCALE));
        defines.put("EMPTY_DENSITY", glsl(EMPTY_DENSITY));
        defines.put("OPAQUE_TRANSMITTANCE", glsl(OPAQUE_TRANSMITTANCE));
        defines.put("OPACITY_STEP_GROWTH", glsl(OPACITY_STEP_GROWTH));
        defines.put("FULL_DENSITY", glsl(FULL_DENSITY));
        defines.put("SHADOW_MIN_TRANSMITTANCE", glsl(SHADOW_MIN_TRANSMITTANCE));
        defines.put("CLEAR_TRANSMITTANCE", glsl(CLEAR_TRANSMITTANCE));
        defines.put("EXTINCTION", glsl(SmokeVolumes.EXTINCTION_PER_BLOCK));
        defines.put("EROSION_SHAPE", glsl(EROSION_SHAPE));
        defines.put("EROSION_DETAIL", glsl(EROSION_DETAIL));
        defines.put("WARP_BLOCKS", glsl(WARP_BLOCKS));
        defines.put("DETAIL_SCALE", glsl(DETAIL_SCALE));
        defines.put("SHADOW_DISTANCE", glsl(SHADOW_DISTANCE));
        defines.put("SHADOW_STRENGTH", glsl(SHADOW_STRENGTH));
        defines.put("AMBIENT_SHARE", glsl(AMBIENT_SHARE));
        defines.put("BREATHE_DEPTH", glsl(BREATHE_DEPTH));
        defines.put("BILLOW_SHADE", glsl(BILLOW_SHADE));
        defines.put("CHURN_SHADE", glsl(CHURN_SHADE));
        defines.put("UPSAMPLE_DEPTH_EPSILON", glsl(UPSAMPLE_DEPTH_EPSILON));
        defines.put("UPSAMPLE_BILINEAR_FLOOR", glsl(UPSAMPLE_BILINEAR_FLOOR));
        defines.put("UPSAMPLE_FLAT", glsl(UPSAMPLE_FLAT));
        return defines;
    }

    static String glsl(float value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }

    /**
     * Length of the next march segment starting {@code eyeDistance} blocks along the ray. {@code budget} is the
     * marched span divided by {@link #MAX_STEPS}, so non-empty steps always cover the whole span; {@code transmittance}
     * is the view's transmittance so far ({@link #OPACITY_STEP_GROWTH}).
     * 从光线上 {@code eyeDistance} 格处开始的下一段步进长度。{@code budget} 为步进区间除以 {@link #MAX_STEPS}，
     * 因此非空步也总能覆盖整个区间；{@code transmittance} 为到目前为止的透射率（{@link #OPACITY_STEP_GROWTH}）。
     */
    public static float stepLength(float eyeDistance, float budget, boolean empty, float transmittance) {
        float base = Math.max(Math.min(MIN_STEP + STEP_GROWTH * eyeDistance, NEAR_STEP_CAP), budget)
                * (1.0F + OPACITY_STEP_GROWTH * (1.0F - transmittance));
        return empty ? base * EMPTY_STEP_SCALE : base;
    }

    /**
     * Noise erosion of a raw density (0..1 erosion inputs, 1 = most erosion). Never raises density; keeps 1 at 1.
     * 原始浓度的噪声侵蚀（侵蚀输入 0..1，1 = 侵蚀最强）。绝不提高浓度；浓度 1 保持为 1。
     */
    public static float erode(float density, float shapeErosion, float detailErosion) {
        float d = clamp01(density);
        float lo = EROSION_SHAPE * shapeErosion;
        d = clamp01((d - lo) / (1.0F - lo));
        lo = EROSION_DETAIL * detailErosion;
        return clamp01((d - lo) / (1.0F - lo));
    }

    /**
     * Slab test of the ray {@code origin + t * dir} ({@code dir} normalised) against a box, clipped to
     * {@code [0, tMax]}. Writes {@code tNear, tFar} into {@code out} and returns true on a hit.
     * 光线 {@code origin + t * dir}（{@code dir} 已归一化）与盒子的板块求交，裁到 {@code [0, tMax]}。
     * 命中时把 {@code tNear, tFar} 写入 {@code out} 并返回 true。
     */
    public static boolean rayBox(float[] origin, float[] dir, float[] min, float[] max, float tMax, float[] out) {
        float tNear = 0.0F;
        float tFar = tMax;
        for (int axis = 0; axis < 3; axis++) {
            float d = Math.abs(dir[axis]) < 1.0E-6F ? 1.0E-6F : dir[axis];
            float a = (min[axis] - origin[axis]) / d;
            float b = (max[axis] - origin[axis]) / d;
            tNear = Math.max(tNear, Math.min(a, b));
            tFar = Math.min(tFar, Math.max(a, b));
        }
        if (tFar <= tNear) {
            return false;
        }
        out[0] = tNear;
        out[1] = tFar;
        return true;
    }

    /**
     * Normalised weights of the four low-resolution taps around a full-resolution pixel: bilinear weights
     * ({@code fx, fy} in 0..1; tap order (0,0), (1,0), (0,1), (1,1)) divided by the relative eye-distance mismatch,
     * so a tap on another surface across a depth edge loses to one on the pixel's own surface.
     * 全分辨率像素周围 4 个低分辨率采样的归一化权重：双线性权重（{@code fx, fy} 为 0..1；顺序 (0,0)、(1,0)、(0,1)、
     * (1,1)）除以相对视距差，使深度边缘另一侧表面上的采样输给像素自身表面上的采样。
     */
    public static void upsampleWeights(float fullDistance, float[] lowDistances, float fx, float fy, float[] out) {
        float sum = 0.0F;
        for (int k = 0; k < 4; k++) {
            float bx = (k & 1) == 1 ? fx : 1.0F - fx;
            float by = (k >> 1) == 1 ? fy : 1.0F - fy;
            float relative = Math.abs(fullDistance - lowDistances[k]) / Math.max(fullDistance, 1.0E-3F);
            out[k] = (bx * by + UPSAMPLE_BILINEAR_FLOOR) / (UPSAMPLE_DEPTH_EPSILON + relative);
            sum += out[k];
        }
        for (int k = 0; k < 4; k++) {
            out[k] /= sum;
        }
    }

    /** Squared distance from a point to a box (0 inside). / 点到盒子的距离平方（盒内为 0）。 */
    public static double distanceSquaredToBox(double x, double y, double z,
                                              double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - x, 0.0), x - maxX);
        double dy = Math.max(Math.max(minY - y, 0.0), y - maxY);
        double dz = Math.max(Math.max(minZ - z, 0.0), z - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Screen-space pixel rectangle {@code [x0, y0, x1, y1)} that covers a camera-relative box under
     * {@code viewProjection}, padded by {@link #SCREEN_RECT_PADDING}. A box that reaches behind the eye covers the
     * whole screen. Returns false when the box is entirely off screen.
     * 相对相机的盒子在 {@code viewProjection} 下覆盖的屏幕像素矩形 {@code [x0, y0, x1, y1)}，外扩
     * {@link #SCREEN_RECT_PADDING}。伸到眼睛后方的盒子覆盖全屏。盒子完全在屏幕外时返回 false。
     */
    public static boolean screenRect(Matrix4f viewProjection, float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ, int width, int height, int[] rect) {
        float ndcMinX = Float.POSITIVE_INFINITY;
        float ndcMinY = Float.POSITIVE_INFINITY;
        float ndcMaxX = Float.NEGATIVE_INFINITY;
        float ndcMaxY = Float.NEGATIVE_INFINITY;
        int behind = 0;
        Vector4f corner = new Vector4f();
        for (int c = 0; c < 8; c++) {
            corner.set((c & 1) == 0 ? minX : maxX, (c & 2) == 0 ? minY : maxY, (c & 4) == 0 ? minZ : maxZ, 1.0F);
            viewProjection.transform(corner);
            if (corner.w <= 1.0E-4F) {
                behind++;
                continue;
            }
            float x = corner.x / corner.w;
            float y = corner.y / corner.w;
            ndcMinX = Math.min(ndcMinX, x);
            ndcMinY = Math.min(ndcMinY, y);
            ndcMaxX = Math.max(ndcMaxX, x);
            ndcMaxY = Math.max(ndcMaxY, y);
        }
        if (behind == 8) {
            return false;
        }
        if (behind > 0) {
            rect[0] = 0;
            rect[1] = 0;
            rect[2] = width;
            rect[3] = height;
            return true;
        }
        if (ndcMaxX < -1.0F || ndcMinX > 1.0F || ndcMaxY < -1.0F || ndcMinY > 1.0F) {
            return false;
        }
        rect[0] = clamp((int) Math.floor((ndcMinX * 0.5F + 0.5F) * width) - SCREEN_RECT_PADDING, 0, width);
        rect[1] = clamp((int) Math.floor((ndcMinY * 0.5F + 0.5F) * height) - SCREEN_RECT_PADDING, 0, height);
        rect[2] = clamp((int) Math.ceil((ndcMaxX * 0.5F + 0.5F) * width) + SCREEN_RECT_PADDING, 0, width);
        rect[3] = clamp((int) Math.ceil((ndcMaxY * 0.5F + 0.5F) * height) + SCREEN_RECT_PADDING, 0, height);
        return rect[2] > rect[0] && rect[3] > rect[1];
    }

    /**
     * Half-resolution rectangle that the march must fill so every upsample tap of the full-resolution
     * {@code rect} reads a marched texel.
     * 半分辨率矩形：步进必须填满它，才能让全分辨率 {@code rect} 的每个放大采样都读到已步进的纹素。
     */
    public static void lowRect(int[] rect, int lowWidth, int lowHeight, int[] low) {
        low[0] = clamp(Math.floorDiv(rect[0], 2) - 1, 0, lowWidth);
        low[1] = clamp(Math.floorDiv(rect[1], 2) - 1, 0, lowHeight);
        low[2] = clamp((rect[2] + 1) / 2 + 1, 0, lowWidth);
        low[3] = clamp((rect[3] + 1) / 2 + 1, 0, lowHeight);
    }

    /** Half-resolution size of a full-resolution extent (at least 1). / 全分辨率尺寸对应的半分辨率尺寸（至少为 1）。 */
    public static int halfSize(int full) {
        return Math.max(1, (full + 1) / 2);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
