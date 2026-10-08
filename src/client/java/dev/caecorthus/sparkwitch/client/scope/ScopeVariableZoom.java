package dev.caecorthus.sparkwitch.client.scope;

/**
 * Client only. A weapon's continuously variable scope magnification, reusable by any {@link ScopeProfile} (USEC
 * first; nothing here names a role). The weapon picks the range, the wheel resolution and the opening magnification;
 * this class owns the rest:
 * <ul>
 *   <li>a TARGET that each wheel notch multiplies by one geometric step ({@code (max / min) ^ (1 / notchesEndToEnd)}),
 *   so every notch changes the field of view by the same share at any magnification; a fractional (high-resolution
 *   or trackpad) delta moves it proportionally, and the target always stays within [min, max];</li>
 *   <li>a SHOWN magnification that eases toward the target in log space with
 *   {@link ScopeRules#ZOOM_EASE_HALF_LIFE_SECONDS} (vanilla's FOV half-life). The ease is an exact exponential of the
 *   elapsed wall time, so it is frame-rate independent, converges, and never overshoots; reading it several times a
 *   frame changes nothing;</li>
 *   <li>a jump to whichever end of the range is more wheel notches away (log distance: at or below the geometric
 *   midpoint {@code sqrt(min * max)} it goes to max, above it to min).</li>
 * </ul>
 * While the scope is closed nothing is on screen to animate, so a change made then ({@link #jumpToFartherEnd} with
 * {@code open = false}, {@link #settle}, {@link #reset}) takes effect at once; the scope module's own scope-in ease
 * (vanilla's FOV ease in Full-Screen Zoom, the PiP lens ease from 1x) then carries the picture. Client thread only
 * (GLFW wheel callbacks, ticks and frames all run there); presentation only, never synced.
 * 仅客户端。武器可连续调节的瞄准镜倍率，可被任何 {@link ScopeProfile} 复用（USEC 最先使用；这里不涉及任何职业）。武器决定
 * 倍率范围、滚轮分辨率与开镜倍率，其余由本类负责：每格滚轮把「目标」乘以一个几何步长
 * （{@code (max / min) ^ (1 / notchesEndToEnd)}），因此任何倍率下每格改变的视野比例相同；小数（高精度滚轮或触控板）增量按比例
 * 移动，目标始终位于 [min, max]；「显示」倍率在对数空间按 {@link ScopeRules#ZOOM_EASE_HALF_LIFE_SECONDS}（原版视场半衰期）
 * 向目标缓动，缓动是经过时间的精确指数函数，因此与帧率无关、必然收敛、绝不越过目标，每帧读取多次也不会改变结果；跳转到
 * 滚轮格数更远的一端（对数距离：不高于几何中点 {@code sqrt(min * max)} 时跳到最大，高于时跳到最小）。瞄准镜关闭时屏幕上没有
 * 可动画的内容，因此此时的改变（{@code open = false} 的 {@link #jumpToFartherEnd}、{@link #settle}、{@link #reset}）立即生效，
 * 画面由开镜模块自己的开镜缓动（全画面放大中原版的视场缓动、画中画镜内自 1 倍的缓动）承接。仅客户端线程（GLFW 滚轮回调、
 * 刻与帧都在该线程）；纯展示，从不同步。
 */
public final class ScopeVariableZoom {
    /**
     * Once the shown magnification is this close to the target (log units, about 0.01 %), it lands on it exactly.
     * 显示倍率与目标相差小于该值（对数单位，约 0.01 %）时直接落在目标上。
     */
    public static final double SETTLE_LOG_EPSILON = 1.0E-4;
    private static final long NO_SAMPLE = Long.MIN_VALUE;

    private final double minMagnification;
    private final double maxMagnification;
    private final int notchesEndToEnd;
    private final double openingMagnification;
    private double target;
    private double shown;
    private long lastSampleNanos = NO_SAMPLE;

    /**
     * @param minMagnification     lowest magnification, at least 1 / 最低倍率，至少为 1
     * @param maxMagnification     highest magnification, above min / 最高倍率，高于最低倍率
     * @param notchesEndToEnd      whole wheel notches from min to max / 从最低到最高的滚轮格数
     * @param openingMagnification where {@link #reset} puts both values (clamped) / {@link #reset} 设置的倍率（会被钳制）
     */
    public ScopeVariableZoom(double minMagnification, double maxMagnification, int notchesEndToEnd,
                             double openingMagnification) {
        if (!(minMagnification >= 1.0) || !(maxMagnification > minMagnification)
                || !Double.isFinite(maxMagnification) || notchesEndToEnd < 1) {
            throw new IllegalArgumentException("bad zoom range");
        }
        this.minMagnification = minMagnification;
        this.maxMagnification = maxMagnification;
        this.notchesEndToEnd = notchesEndToEnd;
        this.openingMagnification = clamp(openingMagnification, minMagnification, maxMagnification);
        this.target = this.openingMagnification;
        this.shown = this.openingMagnification;
    }

    public double minMagnification() {
        return minMagnification;
    }

    public double maxMagnification() {
        return maxMagnification;
    }

    /** The magnification the player last chose; the shown one eases toward it. / 玩家最后选定的倍率。 */
    public double target() {
        return target;
    }

    /**
     * The shown magnification at {@code nowNanos} (a monotonic clock), after easing toward the target since the last
     * sample. / {@code nowNanos}（单调时钟）时刻的显示倍率，自上次采样起向目标缓动。
     */
    public double shown(long nowNanos) {
        advance(nowNanos);
        return shown;
    }

    /**
     * The wheel turned {@code notches} (positive = zoom in) while the scope is open: the shown value is first brought
     * up to {@code nowNanos} toward the old target, then the target moves. / 瞄准镜打开时滚轮转动 {@code notches} 格（正数为
     * 放大）：先把显示值按旧目标推进到 {@code nowNanos}，再移动目标。
     */
    public void scroll(double notches, long nowNanos) {
        advance(nowNanos);
        target = stepTarget(target, notches, minMagnification, maxMagnification, notchesEndToEnd);
    }

    /**
     * Jumps the target to the end of the range farther from it (see {@link #fartherEnd}). With the scope open the shown
     * value eases there; closed, it lands at once. / 把目标跳到距其更远的一端（见 {@link #fartherEnd}）。瞄准镜打开时显示值
     * 缓动过去；关闭时立即到位。
     */
    public void jumpToFartherEnd(long nowNanos, boolean open) {
        advance(nowNanos);
        target = fartherEnd(target, minMagnification, maxMagnification);
        if (!open) {
            settle();
        }
    }

    /** The shown value lands on the target (the scope closed). / 显示值直接落到目标上（瞄准镜已关闭）。 */
    public void settle() {
        shown = target;
        lastSampleNanos = NO_SAMPLE;
    }

    /** Back to the opening magnification, shown and target alike. / 显示值与目标都回到开镜倍率。 */
    public void reset() {
        target = openingMagnification;
        settle();
    }

    private void advance(long nowNanos) {
        if (lastSampleNanos != NO_SAMPLE && nowNanos > lastSampleNanos) {
            shown = ease(shown, target, (nowNanos - lastSampleNanos) / 1.0E9, ScopeRules.ZOOM_EASE_HALF_LIFE_SECONDS);
        }
        if (lastSampleNanos == NO_SAMPLE || nowNanos > lastSampleNanos) {
            lastSampleNanos = nowNanos;
        }
    }

    // ---- Pure rules (unit-tested) / 纯规则（有单元测试） ----

    /** {@code magnification} kept within [min, max]; a non-finite value means min. / 钳制到 [min, max]；非有限值视为最小。 */
    public static double clamp(double magnification, double min, double max) {
        if (!Double.isFinite(magnification)) {
            return min;
        }
        return Math.max(min, Math.min(max, magnification));
    }

    /**
     * The target after {@code notches} wheel notches: {@code target * (max / min) ^ (notches / notchesEndToEnd)},
     * clamped. Proportional to fractional notches; a non-finite delta changes nothing.
     * 滚动 {@code notches} 格后的目标：{@code target * (max / min) ^ (notches / notchesEndToEnd)}，再钳制。小数格按比例移动；
     * 非有限增量不改变目标。
     */
    public static double stepTarget(double target, double notches, double min, double max, int notchesEndToEnd) {
        double from = clamp(target, min, max);
        if (!Double.isFinite(notches) || notches == 0.0 || notchesEndToEnd < 1) {
            return from;
        }
        double logStep = Math.log(max / min) / notchesEndToEnd;
        // Clamped in log space first, so even an absurd delta cannot overflow exp() to the wrong end.
        // 先在对数空间钳制，即使增量离谱也不会让 exp() 溢出到错误的一端。
        double logTarget = Math.max(Math.log(min), Math.min(Math.log(max), Math.log(from) + notches * logStep));
        return clamp(Math.exp(logTarget), min, max);
    }

    /**
     * One ease step of the shown magnification toward {@code target} over {@code seconds}, in log space: half the
     * remaining ratio per {@code halfLifeSeconds}. It never passes the target, lands on it once within
     * {@link #SETTLE_LOG_EPSILON}, and keeps {@code shown} for a non-positive or non-finite time.
     * 显示倍率在对数空间向 {@code target} 缓动 {@code seconds} 秒：每 {@code halfLifeSeconds} 剩余比例减半。绝不越过目标，
     * 进入 {@link #SETTLE_LOG_EPSILON} 以内即落在目标上；时间非正或非有限时保持 {@code shown}。
     */
    public static double ease(double shown, double target, double seconds, double halfLifeSeconds) {
        if (!(shown > 0.0) || !Double.isFinite(shown)) {
            return target;
        }
        if (!(seconds > 0.0) || !Double.isFinite(seconds) || !(halfLifeSeconds > 0.0)) {
            return shown;
        }
        double gap = Math.log(shown) - Math.log(target);
        double left = gap * Math.pow(0.5, seconds / halfLifeSeconds);
        return Math.abs(left) < SETTLE_LOG_EPSILON ? target : target * Math.exp(left);
    }

    /**
     * The end of [min, max] more wheel notches away from {@code magnification} (log distance): max at or below the
     * geometric midpoint {@code sqrt(min * max)} (a tie zooms in), min above it.
     * [min, max] 中距 {@code magnification} 滚轮格数更多的一端（对数距离）：不高于几何中点 {@code sqrt(min * max)} 时为最大
     * （相等时放大），高于时为最小。
     */
    public static double fartherEnd(double magnification, double min, double max) {
        double from = clamp(magnification, min, max);
        return from * from <= min * max ? max : min;
    }

    /**
     * The FOV multiplier a magnification asks for ({@link ScopeProfile#fovMultiplier}): {@code 1 / magnification}, so
     * 1x is 1 (no zoom) and 4x is 0.25; anything below 1 or non-finite means 1.
     * 倍率对应的视场乘数（{@link ScopeProfile#fovMultiplier}）：{@code 1 / 倍率}，因此 1 倍为 1（不放大）、4 倍为 0.25；
     * 低于 1 或非有限时为 1。
     */
    public static float fovMultiplier(double magnification) {
        if (!(magnification >= 1.0) || !Double.isFinite(magnification)) {
            return 1.0F;
        }
        return (float) (1.0 / magnification);
    }
}
