package dev.caecorthus.sparkwitch.client.bear.smoke.render;

/**
 * Pure, render-only motion of the visible smoke (owner 2026-11-05: a formed cloud keeps its cell region but must
 * never look frozen). The two noise octaves are advected along different paths: the shape octave rises slowly (hot
 * smoke) while circling one way, the detail octave rises more slowly while circling the other way faster, so the
 * smoke churns instead of scrolling, and the combined field does not repeat within {@link #NO_REPEAT_SECONDS}.
 * Edges "breathe" through a slow, noise-phased swing of the erosion strength. None of this moves density outside the
 * trilinear edge zone plus the domain warp (about half a block), and nothing reaches the server or the outline mask.
 * 可见烟雾的纯渲染运动（所有者 2026-11-05：成形后的烟团格子区域不变，但绝不能看起来静止）。两个噪声倍频沿不同路径平流：
 * 形状倍频缓慢上升（热烟）并朝一个方向绕圈，细节倍频上升更慢、反向绕得更快，因此烟是翻滚而不是平移，且合成场在
 * {@link #NO_REPEAT_SECONDS} 内不重复。边缘通过噪声相位错开的侵蚀强度慢摆而“呼吸”。这些都不会把浓度移出三线性边缘带加
 * 域扭曲（约半格）之外，也不会影响服务端或描边遮罩。
 */
public final class SmokeMotion {
    /** Upward speed of the shape octave, blocks per second. / 形状倍频上升速度（格/秒）。 */
    public static final double SHAPE_RISE = 0.2;
    /** Radius (blocks) and period (seconds) of the shape octave's circle. / 形状倍频绕圈的半径（格）与周期（秒）。 */
    public static final double SHAPE_SWIRL_RADIUS = 1.2;
    public static final double SHAPE_SWIRL_SECONDS = 21.0;
    public static final double DETAIL_RISE = 0.1;
    public static final double DETAIL_SWIRL_RADIUS = 0.6;
    public static final double DETAIL_SWIRL_SECONDS = 9.0;
    /** Period of the edge breathing, seconds. / 边缘呼吸周期（秒）。 */
    public static final double BREATHE_SECONDS = 5.0;
    /** Window in which the advected field must not repeat. / 平流场不得重复的时间窗口。 */
    public static final double NO_REPEAT_SECONDS = 20.0;

    private SmokeMotion() {
    }

    /**
     * Writes the shape-octave offset (out[0..2]) and detail-octave offset (out[3..5]) in noise-tile units, already
     * wrapped to 0..1, for a camera at {@code (cx, cy, cz)} at {@code seconds} of world time; the shader adds them to
     * {@code cameraRelative * frequency}.
     * 写出形状倍频偏移（out[0..2]）与细节倍频偏移（out[3..5]），单位为噪声平铺、已折回 0..1；相机位于
     * {@code (cx, cy, cz)}，世界时间 {@code seconds} 秒；着色器把它们加到 {@code 相对相机坐标 * 频率} 上。
     */
    public static void noiseOffsets(double cx, double cy, double cz, double seconds, float[] out) {
        double period = SmokeMarchRules.NOISE_PERIOD_BLOCKS;
        double detailScale = SmokeMarchRules.DETAIL_SCALE;
        double shapeAngle = seconds * (2.0 * Math.PI / SHAPE_SWIRL_SECONDS);
        double detailAngle = -seconds * (2.0 * Math.PI / DETAIL_SWIRL_SECONDS);
        // Sampling at (x - motion) moves the pattern by +motion. / 在 (x - 位移) 处采样即图案移动 +位移。
        out[0] = tile((cx - SHAPE_SWIRL_RADIUS * Math.sin(shapeAngle)) / period);
        out[1] = tile((cy - SHAPE_RISE * seconds) / period);
        out[2] = tile((cz - SHAPE_SWIRL_RADIUS * (1.0 - Math.cos(shapeAngle))) / period);
        out[3] = tile((cx - DETAIL_SWIRL_RADIUS * Math.sin(detailAngle)) * detailScale / period);
        out[4] = tile((cy - DETAIL_RISE * seconds) * detailScale / period);
        out[5] = tile((cz - DETAIL_SWIRL_RADIUS * (1.0 - Math.cos(detailAngle))) * detailScale / period);
    }

    /** Breathing phase in radians, 0..2π. / 呼吸相位（弧度，0..2π）。 */
    public static float breathePhase(double seconds) {
        double cycles = seconds / BREATHE_SECONDS;
        return (float) ((cycles - Math.floor(cycles)) * 2.0 * Math.PI);
    }

    private static float tile(double value) {
        return (float) (value - Math.floor(value));
    }
}
