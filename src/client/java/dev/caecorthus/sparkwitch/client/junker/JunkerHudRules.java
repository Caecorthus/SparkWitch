package dev.caecorthus.sparkwitch.client.junker;

import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure layout of the Ragpicker's owner-only HUD. Two pieces, never both at once:
 * <ul>
 *   <li>the corner skill line (ready hint or cooldown), right-aligned with the shared skill line's 5 px padding. The
 *       Saint's Karma line reserves this row ({@code SaintHudLayoutRules}). When the line would reach the centred
 *       hotbar column (narrow GUI widths) it is lifted above the hotbar, Wathe's stamina row and its cooldown number
 *       (Knight / Fiend Dash / Seeker precedent);</li>
 *   <li>while searching, the percentage with the identity-coloured bar under it, centred in the shared "above the
 *       action bar" band and stacked above the Control Expert status block whenever that block can show.</li>
 * </ul>
 * 拾荒者仅本人可见 HUD 的纯布局。两部分，不会同时出现：
 * <ul>
 *   <li>右下角技能行（就绪提示或冷却），与共享技能行一样右对齐、5 像素边距；圣徒业障行会为此行预留位置
 *       （{@code SaintHudLayoutRules}）。该行会伸入居中快捷栏列时（GUI 较窄）抬升到快捷栏、Wathe 体力行及其冷却数字之上
 *       （沿用骑士、魔人疾驰与搜寻者）；</li>
 *   <li>搜刮中：百分比及其下方的身份色进度条，居中于共享的“动作栏上方”显示带，控场专家状态块可能显示时叠放在其上方。</li>
 * </ul>
 */
public final class JunkerHudRules {
    public static final String READY_KEY = "tip.sparkwitch.ragpicker.ready";
    public static final String COOLDOWN_KEY = "tip.sparkwitch.ragpicker.cooldown";
    public static final String SEARCHING_KEY = "tip.sparkwitch.ragpicker.searching";

    public static final int RIGHT_PADDING = 5;
    public static final int BOTTOM_PADDING = 5;
    /** Half the vanilla hotbar width (182 / 2). / 原版快捷栏宽度的一半（182 / 2）。 */
    public static final int HOTBAR_HALF_WIDTH = 91;
    /** Horizontal gap kept from the hotbar. / 与快捷栏保持的水平间距。 */
    public static final int HOTBAR_GAP = 4;
    /**
     * Bottom offset of the lifted line: above Wathe's stamina row (top at height - 39) and its cooldown number, + 2 px.
     * 抬升后该行的底部偏移：位于 Wathe 体力行（顶端为 height - 39）及其冷却数字之上，再留 2 像素。
     */
    public static final int LIFTED_BOTTOM_OFFSET = 41;
    /**
     * Seconds used only to measure the cooldown line, so the lift does not flip as the countdown loses a digit.
     * 仅用于测量冷却行宽度的秒数，使抬升判断不会因倒计时少一位数字而来回跳动。
     */
    public static final int MEASURE_SECONDS = 99;

    public static final int BAR_WIDTH = 120;
    public static final int BAR_HEIGHT = 4;
    /** Rows between the percentage text and the bar. / 百分比文字与进度条之间的间距。 */
    public static final int TEXT_BAR_GAP = 2;
    /** The backing plate reaches this far around the bar. / 底板超出进度条的宽度。 */
    public static final int BAR_BORDER = 1;
    /** Air kept between the readout and whatever sits under it. / 读数与其下方内容之间保留的间距。 */
    public static final int READOUT_GAP = 2;

    private JunkerHudRules() {
    }

    /**
     * The corner line: its key, whether it takes the ability-key hint, and its seconds (negative = none).
     * 右下角行：翻译键、是否带技能按键提示、秒数（负数表示无）。
     */
    public record Line(String key, boolean keyHint, int seconds) {
        public boolean hasSeconds() {
            return seconds >= 0;
        }
    }

    /**
     * Cooldown in whole seconds rounded up while any remains, else the ready hint with the ability key.
     * 仍有冷却时显示向上取整的整秒数，否则显示带技能按键的就绪提示。
     */
    public static Line cornerLine(int cooldownRemainingTicks) {
        if (cooldownRemainingTicks > 0) {
            return new Line(COOLDOWN_KEY, false, TickTime.ceilSeconds(cooldownRemainingTicks));
        }
        return new Line(READY_KEY, true, -1);
    }

    /** The seconds a line is measured with (see {@link #MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return seconds < 0 ? seconds : Math.max(seconds, MEASURE_SECONDS);
    }

    /** Left x of the right-aligned corner line. / 右对齐右下角行的左侧 x。 */
    public static int cornerX(int scaledWidth, int textWidth) {
        return Math.max(0, scaledWidth - RIGHT_PADDING - Math.max(0, textWidth));
    }

    /** Whether a line starting at {@code lineX} reaches the centred hotbar column. / 从 lineX 开始的行是否伸入快捷栏列。 */
    public static boolean overlapsHotbar(int scaledWidth, int lineX) {
        return lineX < scaledWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
    }

    /** Top y of the corner line, lifted when the measured line reaches the hotbar column. / 右下角行的顶部 y。 */
    public static int cornerY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight) {
        int bottomOffset = overlapsHotbar(scaledWidth, cornerX(scaledWidth, measuredWidth))
                ? LIFTED_BOTTOM_OFFSET
                : BOTTOM_PADDING;
        return scaledHeight - bottomOffset - fontHeight;
    }

    /** Whole percent of a 0..1 progress, floored and clamped. / 0..1 进度的整百分比，向下取整并限制范围。 */
    public static int percent(float progress) {
        if (!(progress > 0.0F)) {
            return 0;
        }
        return Math.min(100, (int) (progress * 100.0F));
    }

    /** Filled bar width for a 0..1 progress. / 0..1 进度对应的填充宽度。 */
    public static int filledWidth(float progress) {
        if (!(progress > 0.0F)) {
            return 0;
        }
        return Math.min(BAR_WIDTH, (int) (BAR_WIDTH * progress));
    }

    /** Height from the text top to the backing plate's bottom edge. / 从文字顶部到底板底边的高度。 */
    public static int readoutHeight(int fontHeight) {
        return fontHeight + TEXT_BAR_GAP + BAR_HEIGHT + BAR_BORDER;
    }

    /**
     * Top y of the percentage text so the whole readout ends {@link #READOUT_GAP} above {@code bandTop}: the shared
     * band's bottom edge, or the top of the Control Expert block when it can show (its own clearance keeps the vanilla
     * action bar, the held-item name and the stamina rows below).
     * 百分比文字的顶部 y，使整个读数在 bandTop 上方 READOUT_GAP 处结束：bandTop 为共享显示带的底边，或控场专家状态块可能
     * 显示时该块的顶部（其自身的间距已让原版动作栏、手持物品名与体力行位于下方）。
     */
    public static int searchTextY(int bandTop, int fontHeight) {
        return bandTop - READOUT_GAP - readoutHeight(fontHeight);
    }

    /** Top y of the bar's fill, under the text. / 文字下方进度条填充部分的顶部 y。 */
    public static int barY(int textY, int fontHeight) {
        return textY + fontHeight + TEXT_BAR_GAP;
    }
}
