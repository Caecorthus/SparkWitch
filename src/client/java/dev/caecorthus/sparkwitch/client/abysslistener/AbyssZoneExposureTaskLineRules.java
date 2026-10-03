package dev.caecorthus.sparkwitch.client.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;

/**
 * Pure layout and style math for the display-only 「快离开这里！！！」 line, mirroring Wathe's {@code MoodRenderer} task list:
 * lines are 10 px apart from y = 6, the mood bar sits under the line with the largest offset, rows fade with
 * {@code lerp(delta / 16)} and leave the layout below the same alpha cutoff. The pseudo line is always the last row.
 * 仅用于显示的「快离开这里！！！」行的纯布局与样式计算，仿照 Wathe {@code MoodRenderer} 任务列表：各行从 y = 6 起间隔 10 像素，
 * 理智条位于偏移最大的行下方，行以 {@code lerp(delta / 16)} 淡入淡出，并在相同的透明度阈值以下离开布局。临时任务行始终是最后一行。
 */
public final class AbyssZoneExposureTaskLineRules {
    /** Wathe's line spacing and first-line y. / Wathe 的行距与首行 y。 */
    public static final int LINE_HEIGHT = 10;
    public static final int FIRST_LINE_Y = 6;
    public static final int TEXT_X = 22;
    /** Wathe's default bar width when no task line exists. / 没有任务行时 Wathe 的默认理智条宽度。 */
    public static final float NO_TASK_TEXT_WIDTH = 100.0F;
    /** Wathe drops a task row below this alpha. / Wathe 在此透明度以下移除任务行。 */
    public static final float ALPHA_CUTOFF = 0.075F;

    private AbyssZoneExposureTaskLineRules() {
    }

    /** Blinks between the bright and the dim sculk color every blink period. / 每个闪烁周期在亮青与暗青之间切换。 */
    public static int color(long ageTicks) {
        return Math.floorMod(ageTicks / AbyssListenerRules.PSEUDO_TASK_BLINK_TICKS, 2) == 0
                ? AbyssListenerRules.PSEUDO_TASK_COLOR
                : AbyssListenerRules.PSEUDO_TASK_DIM_COLOR;
    }

    /** Wathe's row fade: {@code lerp(delta / 16, alpha, visible ? 1 : 0)}. / Wathe 的行淡入淡出。 */
    public static float nextAlpha(float alpha, float delta, boolean visible) {
        return lerp(delta / 16.0F, alpha, visible ? 1.0F : 0.0F);
    }

    /**
     * A fade is stale, and dropped at once, when Wathe's layout path skipped the line since the previous frame and the
     * local player is not exposed; an exposed player keeps it (the line resumes when the layout runs again).
     * 当自上一帧以来 Wathe 布局跳过了本行且本地玩家未暴露时，淡入淡出即为过期并立即丢弃；暴露中的玩家保留它（布局恢复后本行继续显示）。
     */
    public static boolean dropsStaleFade(boolean laidOutSinceLastFrame, boolean visible) {
        return !laidOutSinceLastFrame && !visible;
    }

    /** Same cutoff Wathe uses to drop a fading row. / 与 Wathe 移除淡出行相同的阈值。 */
    public static boolean inLayout(float alpha) {
        return alpha >= ALPHA_CUTOFF && ((((int) (alpha * 255.0F)) << 24) & 0xFC000000) != 0;
    }

    /** ARGB with Wathe's alpha encoding. / 使用 Wathe 透明度编码的 ARGB。 */
    public static int argb(int rgb, float alpha) {
        return (rgb & 0xFFFFFF) | ((int) (alpha * 255.0F) << 24);
    }

    /** Row offset of the pseudo line: below the last Wathe row, else the first row. / 临时任务行的行偏移。 */
    public static float lineOffset(boolean hasWatheRow, float maxWatheOffset) {
        return hasWatheRow ? maxWatheOffset + 1.0F : 0.0F;
    }

    public static float lineY(float offset) {
        return FIRST_LINE_Y + LINE_HEIGHT * offset;
    }

    /**
     * Correction to Wathe's freshly lerped {@code moodOffset} so it converges on the pseudo line instead:
     * {@code lerp(t, x, b) = lerp(t, x, a) + t·(b − a)} with {@code t = delta / 8}. Wathe's own target is the last
     * row's offset, or 0 without rows (where the pseudo line also sits, so no correction).
     * 对 Wathe 本帧刚插值的 {@code moodOffset} 的修正，使其改为收敛到临时任务行：
     * {@code lerp(t, x, b) = lerp(t, x, a) + t·(b − a)}，{@code t = delta / 8}。Wathe 自身目标为最后一行的偏移，
     * 无行时为 0（临时任务行也位于 0，因此无需修正）。
     */
    public static float moodOffsetCorrection(float delta, boolean hasWatheRow, float maxWatheOffset) {
        float watheTarget = hasWatheRow ? maxWatheOffset : 0.0F;
        return delta / 8.0F * (lineOffset(hasWatheRow, maxWatheOffset) - watheTarget);
    }

    /**
     * Same correction for {@code moodTextWidth} ({@code t = delta / 32}): the bar follows the pseudo line's width
     * instead of the last Wathe row's width (or Wathe's 100 px default without rows).
     * 对 {@code moodTextWidth} 的同类修正（{@code t = delta / 32}）：理智条宽度改为跟随临时任务行，而非最后一个 Wathe 行
     * （无行时为 Wathe 默认的 100 像素）。
     */
    public static float moodTextWidthCorrection(float delta, boolean hasWatheRow, float lastWatheWidth, float lineWidth) {
        float watheTarget = hasWatheRow ? lastWatheWidth : NO_TASK_TEXT_WIDTH;
        return delta / 32.0F * (lineWidth - watheTarget);
    }

    private static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }
}
