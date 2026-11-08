package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeTimeline;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure rules of the visible BEAR smoke: the ray-march constants the GLSL receives as {@code #define}s (one source of
 * truth, see {@link #shaderDefines()}), and Java mirrors of the shader's ray-box, step, erosion and upsample math.
 * The CPU uses the culling helpers each frame; the mirrors document and pin what the shader does.
 * Noise erosion only ever removes density; the domain warp moves the visible boundary by at most
 * {@link #warpReach()} blocks either way (ragged wisps outside, curls and thin patches inside), so an outline mask
 * that marches the raw density with {@link SmokeVolumes#EXTINCTION_PER_BLOCK} agrees with the visible smoke away from
 * that band.
 * BEAR 可见烟雾的纯规则：GLSL 以 {@code #define} 形式接收的光线步进常量（唯一来源，见 {@link #shaderDefines()}），以及
 * 着色器中光线-盒子、步长、侵蚀与放大算法的 Java 镜像。CPU 每帧使用裁剪辅助函数；镜像用于说明并固定着色器行为。
 * 噪声侵蚀只会减少浓度；域扭曲把可见边界向内外各移动至多 {@link #warpReach()} 格（外侧是破碎烟丝，内侧是卷曲与稀薄
 * 区域），因此用 {@link SmokeVolumes#EXTINCTION_PER_BLOCK} 步进原始浓度的描边遮罩，在该边界带以外与可见烟雾一致。
 */
public final class SmokeMarchRules {
    /** Clouds marched per pixel; farther clouds are dropped. / 每像素步进的烟团数；更远的被舍弃。 */
    public static final int MAX_CLOUDS = 8;
    /**
     * Clouds per march that get an empty-space texture ({@link SmokeOccupancy}); later ones are always sampled.
     * 每次步进带空区纹理（{@link SmokeOccupancy}）的烟团数；之后的烟团总是照常采样。
     */
    public static final int OCCUPANCY_CLOUDS = 4;
    /**
     * Cloud-slot counts the march is compiled for ({@code CLOUD_SLOTS}): a pass uses the smallest that holds its
     * clouds, so a one-cloud view does not run eight-slot loops. Results are identical, since slots past
     * {@code CloudCount} are skipped anyway.
     * 步进着色器按这些烟团槽数编译（{@code CLOUD_SLOTS}）：每次渲染取能容纳其烟团的最小值，使只有一团烟的画面不必跑 8 槽循环。
     * 结果完全相同，因为超出 {@code CloudCount} 的槽本来就会跳过。
     */
    static final int[] CLOUD_SLOT_VARIANTS = {1, 2, 4, MAX_CLOUDS};
    /** Hard cap on march steps per half-resolution pixel. / 每个半分辨率像素的步数上限。 */
    public static final int MAX_STEPS = 48;
    /** Step near the eye, in blocks (optical depth 0.45 per step in full smoke). / 眼前的步长（格；满浓度时每步光学厚度 0.45）。 */
    public static final float MIN_STEP = 0.3F;
    /** Extra step length per block of eye distance. / 每格视距增加的步长。 */
    public static final float STEP_GROWTH = 0.015F;
    /** Largest distance-grown step before the step budget takes over. / 步数预算接管之前，随距离增长的最大步长。 */
    public static final float NEAR_STEP_CAP = 1.0F;
    /** A step after an empty sample is this much longer. / 空样本之后的一步加长倍数。 */
    public static final float EMPTY_STEP_SCALE = 2.0F;
    public static final float EMPTY_DENSITY = 0.002F;
    /**
     * Below this transmittance a sample is mostly hidden: fine detail, sky occlusion and the light march are skipped
     * and the last lighting is reused.
     * 透射率低于此值时样本大多已被遮住：跳过细节、天空遮蔽与光线步进，沿用上一次的光照。
     */
    public static final float VISIBLE_TRANSMITTANCE = 0.3F;
    /** Transmittance at which the march stops and counts the rest as opaque. / 步进停止并视余量为不透明的透射率。 */
    public static final float OPAQUE_TRANSMITTANCE = 0.02F;
    /**
     * Steps lengthen as the view fills up: at transmittance T a step is {@code 1 + this * (1 - T)} times longer, since
     * what lies behind contributes at most T.
     * 视线被填满时步长随之变长：透射率为 T 时步长乘以 {@code 1 + 本值 * (1 - T)}，因为其后的内容最多贡献 T。
     */
    public static final float OPACITY_STEP_GROWTH = 1.0F;
    /**
     * Steps lengthen in thin smoke: after a sample of density d the next step is {@code 1 + this * (1 - d)} times
     * longer (thin smoke integrates accurately with long steps).
     * 稀薄的烟中步长变长：浓度为 d 的样本之后，下一步乘以 {@code 1 + 本值 * (1 - d)}（稀烟用长步积分也足够准确）。
     */
    public static final float THIN_STEP_GROWTH = 0.8F;
    /** Composite skips pixels at least this clear. / 合成跳过透射率不低于此值的像素。 */
    public static final float CLEAR_TRANSMITTANCE = 0.998F;
    /**
     * Big-billow erosion: whole regions of the cloud thin out, giving thick and thin parts.
     * 大团块侵蚀：整片区域变稀，形成浓淡不均。
     */
    public static final float EROSION_PATCH = 0.7F;
    /** Shape-octave erosion strength. / 形状倍频侵蚀强度。 */
    public static final float EROSION_SHAPE = 0.65F;
    /** Detail-octave erosion strength (ragged, semi-transparent edges). / 细节倍频侵蚀强度（破碎、半透明的边缘）。 */
    public static final float EROSION_DETAIL = 0.6F;
    /**
     * Even full smoke thins by up to this share in the big-billow troughs, so the core has gradients too.
     * 即使是满浓度的烟，在大团块的低谷处也最多变稀这么多，使核心也有浓淡变化。
     */
    public static final float INTERIOR_THINNING = 0.25F;
    /** Largest big-billow warp offset, in blocks. / 大团块扭曲的最大偏移（格）。 */
    public static final float BIG_WARP = 1.0F;
    /** Largest shape-octave warp offset (curls), in blocks. / 形状倍频扭曲（卷曲）的最大偏移（格）。 */
    public static final float WARP_BLOCKS = 0.45F;
    /** Largest detail-octave warp offset (fine curls), in blocks. / 细节倍频扭曲（细小卷曲）的最大偏移（格）。 */
    public static final float FINE_WARP = 0.35F;
    /** World size of one noise tile, in blocks. / 一块噪声平铺的世界尺寸（格）。 */
    public static final float NOISE_PERIOD_BLOCKS = 6.0F;
    /** Big-billow frequency relative to the shape octave (a 14-block tile). / 大团块频率相对形状倍频（14 格平铺）。 */
    public static final float BIG_SCALE = 0.42F;
    /** Detail octave frequency relative to the shape octave. / 细节倍频相对形状倍频的频率。 */
    public static final float DETAIL_SCALE = 2.71F;
    /** Height of the sky-occlusion tap above each sample, in blocks. / 天空遮蔽采样点在样本上方的高度（格）。 */
    public static final float AO_DISTANCE = 0.8F;
    /** Ambient left on fully occluded smoke. / 完全被遮蔽的烟仍保留的环境光比例。 */
    public static final float AMBIENT_MIN = 0.75F;
    /** Ambient share of the lightmap colour for smoke under the sky (the sun adds the rest). / 见天的烟取光照图颜色的环境光比例（其余由太阳补足）。 */
    public static final float OUTDOOR_AMBIENT = 0.7F;
    /** Ambient share indoors, where lamps are the only light. / 室内的环境光比例（灯是唯一光源）。 */
    public static final float INDOOR_AMBIENT = 0.9F;
    /** How far sky-exposed shadows lean toward the sky colour. / 见天阴影偏向天空颜色的程度。 */
    public static final float SKY_TINT = 0.15F;
    /**
     * Density scale of the light march, below 1 to stand in for multiple scattering (shadowed smoke stays grey).
     * 光线步进的浓度系数，小于 1 以近似多次散射（阴影中的烟保持灰色）。
     */
    public static final float LIGHT_DENSITY_SCALE = 0.45F;
    /** Henyey-Greenstein asymmetry and its share in the phase (forward scattering). / HG 不对称因子及其在相函数中的比例（前向散射）。 */
    public static final float PHASE_G = 0.45F;
    public static final float PHASE_SHARE = 0.5F;
    /** Sun strength at full daylight, and the moon's. / 白天满日照时太阳的强度，以及月亮的强度。 */
    public static final float SUN_STRENGTH = 0.5F;
    public static final float MOON_STRENGTH = 0.08F;
    /**
     * Share of the shape erosion that swings with the edge breathing ({@link SmokeMotion}).
     * 形状侵蚀中随边缘呼吸摆动的比例（{@link SmokeMotion}）。
     */
    public static final float BREATHE_DEPTH = 0.4F;
    /** How much the advected noise varies the sky occlusion. / 平流噪声对天空遮蔽的调制幅度。 */
    public static final float BILLOW_SHADE = 0.6F;
    /** How much the advected noise varies the brightness. / 平流噪声对亮度的调制幅度。 */
    public static final float CHURN_SHADE = 0.08F;
    /** Grey-white screening-smoke albedo. / 灰白色遮蔽烟的反照率。 */
    public static final float ALBEDO_R = 0.76F;
    public static final float ALBEDO_G = 0.76F;
    public static final float ALBEDO_B = 0.78F;
    /** Relative depth difference that halves an upsample tap's weight. / 使放大采样权重减半的相对深度差。 */
    public static final float UPSAMPLE_DEPTH_EPSILON = 0.05F;
    /**
     * Relative depth spread below which the composite's 3x3 tent (grain removal) lies on one surface; above it the
     * nearest-depth weights of {@link #upsampleWeights} keep smoke off near silhouettes.
     * 相对深度差低于此值时，合成的 3x3 帐篷滤波（去颗粒）位于同一表面；高于此值时用 {@link #upsampleWeights} 的最近深度
     * 权重，使烟不会渗到近处的轮廓上。
     */
    public static final float UPSAMPLE_FLAT = 0.06F;
    /** Keeps a texel that sits exactly on a bilinear zero selectable by depth. / 让恰好处于双线性零权重位置的纹素仍可按深度被选中。 */
    public static final float UPSAMPLE_BILINEAR_FLOOR = 0.001F;
    /** Interleaved-gradient-noise frame offset (Jimenez 2014). / 交错梯度噪声逐帧偏移（Jimenez 2014）。 */
    public static final float JITTER_FRAME_OFFSET = 5.588238F;
    /** Padding of the scissor rectangle, in full-resolution pixels. / 裁剪矩形的外扩（全分辨率像素）。 */
    public static final int SCREEN_RECT_PADDING = 4;
    /**
     * Extra reach of the per-pixel tunnel cull, in blocks, so float rounding never culls a tunnel the ray touches.
     * 逐像素通道剔除的额外余量（格），使浮点舍入永远不会剔掉视线实际触及的通道。
     */
    public static final float TUNNEL_CULL_SLACK = 0.05F;

    private SmokeMarchRules() {
    }

    /** The {@code #define}s every smoke program is compiled with. / 所有烟雾程序编译时注入的 {@code #define}。 */
    public static Map<String, String> shaderDefines() {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("MAX_CLOUDS", Integer.toString(MAX_CLOUDS));
        defines.put("OCCUPANCY_CLOUDS", Integer.toString(OCCUPANCY_CLOUDS));
        defines.put("MAX_STEPS", Integer.toString(MAX_STEPS));
        defines.put("MIN_STEP", glsl(MIN_STEP));
        defines.put("STEP_GROWTH", glsl(STEP_GROWTH));
        defines.put("NEAR_STEP_CAP", glsl(NEAR_STEP_CAP));
        defines.put("EMPTY_STEP_SCALE", glsl(EMPTY_STEP_SCALE));
        defines.put("EMPTY_DENSITY", glsl(EMPTY_DENSITY));
        defines.put("OPAQUE_TRANSMITTANCE", glsl(OPAQUE_TRANSMITTANCE));
        defines.put("OPACITY_STEP_GROWTH", glsl(OPACITY_STEP_GROWTH));
        defines.put("THIN_STEP_GROWTH", glsl(THIN_STEP_GROWTH));
        defines.put("VISIBLE_TRANSMITTANCE", glsl(VISIBLE_TRANSMITTANCE));
        defines.put("CLEAR_TRANSMITTANCE", glsl(CLEAR_TRANSMITTANCE));
        defines.put("EXTINCTION", glsl(SmokeVolumes.EXTINCTION_PER_BLOCK));
        defines.put("EROSION_PATCH", glsl(EROSION_PATCH));
        defines.put("EROSION_SHAPE", glsl(EROSION_SHAPE));
        defines.put("EROSION_DETAIL", glsl(EROSION_DETAIL));
        defines.put("INTERIOR_THINNING", glsl(INTERIOR_THINNING));
        defines.put("BIG_WARP", glsl(BIG_WARP));
        defines.put("WARP_BLOCKS", glsl(WARP_BLOCKS));
        defines.put("FINE_WARP", glsl(FINE_WARP));
        defines.put("WARP_REACH", glsl(warpReach()));
        defines.put("BIG_SCALE", glsl(BIG_SCALE));
        defines.put("DETAIL_SCALE", glsl(DETAIL_SCALE));
        defines.put("AO_DISTANCE", glsl(AO_DISTANCE));
        defines.put("AMBIENT_MIN", glsl(AMBIENT_MIN));
        defines.put("OUTDOOR_AMBIENT", glsl(OUTDOOR_AMBIENT));
        defines.put("INDOOR_AMBIENT", glsl(INDOOR_AMBIENT));
        defines.put("SKY_TINT", glsl(SKY_TINT));
        defines.put("LIGHT_DENSITY_SCALE", glsl(LIGHT_DENSITY_SCALE));
        defines.put("PHASE_G", glsl(PHASE_G));
        defines.put("PHASE_SHARE", glsl(PHASE_SHARE));
        defines.put("BREATHE_DEPTH", glsl(BREATHE_DEPTH));
        defines.put("BILLOW_SHADE", glsl(BILLOW_SHADE));
        defines.put("CHURN_SHADE", glsl(CHURN_SHADE));
        defines.put("UPSAMPLE_DEPTH_EPSILON", glsl(UPSAMPLE_DEPTH_EPSILON));
        defines.put("UPSAMPLE_BILINEAR_FLOOR", glsl(UPSAMPLE_BILINEAR_FLOOR));
        defines.put("UPSAMPLE_FLAT", glsl(UPSAMPLE_FLAT));
        defines.put("MAX_TUNNELS", Integer.toString(SmokeVolumes.MAX_RENDER_TUNNELS));
        defines.put("TUNNEL_EDGE", glsl(SmokeTimeline.TUNNEL_EDGE));
        defines.put("TUNNEL_CULL_SLACK", glsl(TUNNEL_CULL_SLACK));
        return defines;
    }

    /**
     * Index into {@link #CLOUD_SLOT_VARIANTS} of the smallest variant holding {@code clouds}.
     * 能容纳 {@code clouds} 的最小变体在 {@link #CLOUD_SLOT_VARIANTS} 中的下标。
     */
    static int cloudSlotVariant(int clouds) {
        for (int i = 0; i < CLOUD_SLOT_VARIANTS.length - 1; i++) {
            if (clouds <= CLOUD_SLOT_VARIANTS[i]) {
                return i;
            }
        }
        return CLOUD_SLOT_VARIANTS.length - 1;
    }

    static String glsl(float value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }

    /**
     * Length of the next march segment starting {@code eyeDistance} blocks along the ray. {@code budget} is the
     * marched span divided by {@link #MAX_STEPS}, so non-empty steps always cover the whole span; {@code transmittance}
     * is the view's transmittance so far ({@link #OPACITY_STEP_GROWTH}) and {@code lastDensity} the previous sample's
     * density ({@link #THIN_STEP_GROWTH}).
     * 从光线上 {@code eyeDistance} 格处开始的下一段步进长度。{@code budget} 为步进区间除以 {@link #MAX_STEPS}，
     * 因此非空步也总能覆盖整个区间；{@code transmittance} 为到目前为止的透射率（{@link #OPACITY_STEP_GROWTH}），
     * {@code lastDensity} 为上一个样本的浓度（{@link #THIN_STEP_GROWTH}）。
     */
    public static float stepLength(float eyeDistance, float budget, boolean empty, float transmittance,
                                   float lastDensity) {
        float base = Math.max(Math.min(MIN_STEP + STEP_GROWTH * eyeDistance, NEAR_STEP_CAP), budget)
                * (1.0F + OPACITY_STEP_GROWTH * (1.0F - transmittance))
                * (1.0F + THIN_STEP_GROWTH * (1.0F - lastDensity));
        return empty ? base * EMPTY_STEP_SCALE : base;
    }

    /**
     * Noise erosion of a raw density (0..1 erosion inputs, 1 = most erosion): big-billow, shape and detail remaps,
     * then up to {@link #INTERIOR_THINNING} in big-billow troughs. Never raises density.
     * 原始浓度的噪声侵蚀（侵蚀输入 0..1，1 = 侵蚀最强）：依次做大团块、形状、细节重映射，再在大团块低谷处最多变稀
     * {@link #INTERIOR_THINNING}。绝不提高浓度。
     */
    public static float erode(float density, float patchErosion, float shapeErosion, float detailErosion) {
        float d = clamp01(density);
        d = remap(d, EROSION_PATCH * patchErosion);
        d = remap(d, EROSION_SHAPE * shapeErosion);
        d = remap(d, EROSION_DETAIL * detailErosion);
        return d * (1.0F - INTERIOR_THINNING * patchErosion);
    }

    /** Farthest a density sample can be warped, in blocks. / 浓度采样最远可被扭曲的距离（格）。 */
    public static float warpReach() {
        return BIG_WARP + WARP_BLOCKS + FINE_WARP;
    }

    private static float remap(float d, float lo) {
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
     * {@code rect}, the 3x3 tent included, reads a marched texel.
     * 半分辨率矩形：步进必须填满它，才能让全分辨率 {@code rect} 的每个放大采样（含 3x3 帐篷滤波）都读到已步进的纹素。
     */
    public static void lowRect(int[] rect, int lowWidth, int lowHeight, int[] low) {
        low[0] = clamp(Math.floorDiv(rect[0], 2) - 2, 0, lowWidth);
        low[1] = clamp(Math.floorDiv(rect[1], 2) - 2, 0, lowHeight);
        low[2] = clamp((rect[2] + 1) / 2 + 2, 0, lowWidth);
        low[3] = clamp((rect[3] + 1) / 2 + 2, 0, lowHeight);
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
