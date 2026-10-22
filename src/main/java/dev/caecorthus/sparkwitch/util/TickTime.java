package dev.caecorthus.sparkwitch.util;

/**
 * Tick-to-second rounding shared by HUDs and rules. The variants differ only for zero and negative input, and each
 * call site keeps the variant it already used, so a countdown never changes what it shows.
 * HUD 与规则共用的刻转秒取整。各变体只在零与负数输入上不同，每个调用点保留原先使用的变体，因此倒计时显示不变。
 */
public final class TickTime {
    public static final int TICKS_PER_SECOND = 20;

    private TickTime() {
    }

    /** Whole seconds rounded up; 0 for zero or negative ticks. / 向上取整的整秒；零或负刻返回 0。 */
    public static int ceilSeconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** {@link #ceilSeconds(int)} for world-time spans. / 用于世界时间跨度的 {@link #ceilSeconds(int)}。 */
    public static long ceilSeconds(long ticks) {
        return ticks <= 0L ? 0L : (ticks + TICKS_PER_SECOND - 1L) / TICKS_PER_SECOND;
    }

    /**
     * {@code Math.ceil(ticks / 20.0)} without clamping, so ticks at or below -20 give a negative count.
     * 不截断的 {@code Math.ceil(ticks / 20.0)}：-20 刻及以下得到负数。
     */
    public static int ceilSecondsUnclamped(int ticks) {
        return (int) Math.ceil(ticks / (double) TICKS_PER_SECOND);
    }

    /** Rounded up but never below 1, so a finished timer still shows 1. / 向上取整但不低于 1，结束的计时仍显示 1。 */
    public static int ceilSecondsAtLeastOne(int ticks) {
        return Math.max(1, ceilSecondsUnclamped(ticks));
    }

    /**
     * The bare {@code (ticks + 19) / 20}: integer division truncates toward zero, so only ticks at or below -39 give a
     * negative count.
     * 原始的 {@code (ticks + 19) / 20}：整数除法向零截断，因此只有 -39 刻及以下得到负数。
     */
    public static int ceilSecondsRaw(int ticks) {
        return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }
}
