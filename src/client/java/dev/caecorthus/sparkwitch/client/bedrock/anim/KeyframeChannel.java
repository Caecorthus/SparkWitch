package dev.caecorthus.sparkwitch.client.bedrock.anim;

import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangExpression;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangRuntime;

/**
 * One bone channel (rotation, position or scale) as time-sorted keyframes. Constant values live in {@link #values}
 * and are read without touching Molang; only non-constant axes keep an expression in {@link #expressions}.
 * 一个骨骼通道（旋转、位移或缩放），按时间排序的关键帧。常量值存放在 {@link #values} 中，读取时不经过 Molang；只有非常量的
 * 轴才在 {@link #expressions} 中保留表达式。
 *
 * <p>Interpolation follows Blockbench's {@code BoneAnimator.interpolate} and {@code Keyframe.getCatmullromLerp}
 * (js/animations/timeline_animators.js, keyframe.js): between keyframes A and B the value goes from A's {@code post}
 * to B's {@code pre}; a {@code step} A holds A's {@code post}; if either side is {@code catmullrom} the uniform
 * Catmull-Rom spline of three.js ({@code Interpolations.CatmullRom}) runs through A's predecessor's {@code post} and
 * B's successor's {@code pre}, duplicating the end point when the neighbour is missing or the keyframe has distinct
 * pre/post values; otherwise linear. Before the first keyframe its {@code pre} holds, from the last one onwards its
 * {@code post} holds, and exactly at a keyframe its {@code post} applies (right-continuous).
 * 插值遵循 Blockbench 的 {@code BoneAnimator.interpolate} 与 {@code Keyframe.getCatmullromLerp}：关键帧 A 与 B 之间从 A 的
 * post 过渡到 B 的 pre；A 为 step 时保持 A 的 post；任一侧为 catmullrom 时使用 three.js 的均匀 Catmull-Rom 样条，经过 A
 * 前一帧的 post 与 B 后一帧的 pre，邻帧缺失或关键帧的 pre/post 不同时复制端点；否则线性插值。第一帧之前保持其 pre，最后一帧
 * 起保持其 post，恰好位于关键帧时取其 post（右连续）。</p>
 */
final class KeyframeChannel {
    static final byte LINEAR = 0;
    static final byte CATMULLROM = 1;
    static final byte STEP = 2;

    private static final int STRIDE = 6;
    private static final int POST = 3;

    private final double[] times;
    /** Per keyframe: pre x, y, z, post x, y, z. / 每个关键帧：pre xyz、post xyz。 */
    private final double[] values;
    /** Same layout; null entries (or a null array) are constants. / 相同布局；null 表示常量。 */
    private final MolangExpression[] expressions;
    private final byte[] lerpModes;
    /** Pre differs from post (Blockbench: two data points). / pre 与 post 不同（Blockbench 的两个数据点）。 */
    private final boolean[] split;

    KeyframeChannel(double[] times, double[] values, MolangExpression[] expressions, byte[] lerpModes,
                    boolean[] split) {
        this.times = times;
        this.values = values;
        this.expressions = expressions;
        this.lerpModes = lerpModes;
        this.split = split;
    }

    double lastTime() {
        return times[times.length - 1];
    }

    int keyframeCount() {
        return times.length;
    }

    /** Index of the last keyframe at or before {@code time}, or -1 (binary search). / 二分查找。 */
    int locate(double time) {
        int low = 0;
        int high = times.length - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (times[mid] <= time) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    /** One axis at {@code time}; {@code segment} comes from {@link #locate}. / 某一轴在给定时间的值。 */
    double sample(int segment, double time, int axis, MolangRuntime runtime) {
        int last = times.length - 1;
        if (segment < 0) {
            return value(0, axis, false, runtime);
        }
        if (segment >= last) {
            return value(last, axis, true, runtime);
        }
        int a = segment;
        int b = segment + 1;
        double from = value(a, axis, true, runtime);
        if (lerpModes[a] == STEP) {
            return from;
        }
        double alpha = (time - times[a]) / (times[b] - times[a]);
        double to = value(b, axis, false, runtime);
        if (lerpModes[a] == CATMULLROM || lerpModes[b] == CATMULLROM) {
            double before = a > 0 && !split[a] ? value(a - 1, axis, true, runtime) : from;
            double after = b < last && !split[b] ? value(b + 1, axis, false, runtime) : to;
            return catmullRom(alpha, before, from, to, after);
        }
        return from + (to - from) * alpha;
    }

    private double value(int keyframe, int axis, boolean post, MolangRuntime runtime) {
        int index = keyframe * STRIDE + (post ? POST : 0) + axis;
        if (expressions != null) {
            MolangExpression expression = expressions[index];
            if (expression != null) {
                return expression.evaluate(runtime);
            }
        }
        return values[index];
    }

    /** three.js {@code Interpolations.CatmullRom} (uniform, tension 0.5). / three.js 的均匀 Catmull-Rom。 */
    static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double v0 = (p2 - p0) * 0.5;
        double v1 = (p3 - p1) * 0.5;
        double t2 = t * t;
        double t3 = t * t2;
        return (2.0 * p1 - 2.0 * p2 + v0 + v1) * t3 + (-3.0 * p1 + 3.0 * p2 - 2.0 * v0 - v1) * t2 + v0 * t + p1;
    }
}
