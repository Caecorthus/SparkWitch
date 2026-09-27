package dev.caecorthus.sparkwitch.client.controlexpert;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation rules for the owner-only Control Expert status HUD. The block is centred and stacked upward above
 * the vanilla action bar (drawn at {@code height - 72}), clear of Wathe's held-item name, stamina and hotbar rows.
 * 仅拥有者可见的控场专家状态 HUD 的纯展示规则。文本块水平居中，自原版动作栏（位于 {@code height - 72}）上方
 * 向上堆叠，避开 Wathe 的手持物品名、体力行与快捷栏。
 */
public final class ControlExpertStatusHudRules {
    public static final String DISRUPTED_KEY = "hud.sparkwitch.control_expert.disrupted";
    public static final String STUNNED_KEY = "hud.sparkwitch.control_expert.stunned";
    public static final String INSTINCT_BLOCKED_KEY = "message.sparkwitch.control_expert.instinct_blocked";
    /** Length of the Alt-press flash. / 按下 Alt 后闪烁的持续刻数。 */
    public static final int FLASH_TICKS = 20;
    public static final int DISRUPT_COLOR = 0xFF4DD0E1;
    public static final int FLASH_COLOR = 0xFFFFFFFF;
    public static final int STUN_COLOR = 0xFFFFD54F;
    public static final int BLOCKED_COLOR = 0xFFFF5555;
    static final int LINE_GAP = 2;
    private static final int FLASH_PERIOD_TICKS = 4;
    /** Distance from the screen bottom to the block's bottom edge. / 屏幕底部到文本块底边的距离。 */
    private static final int ACTION_BAR_CLEARANCE = 76;

    private ControlExpertStatusHudRules() {
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    static int seconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    /**
     * A rising edge of the raw instinct key while disrupted but not stunned; a held key or SparkAssist toggle does not
     * retrigger, and while stunned every key is locked anyway.
     * 在受干扰但未眩晕时原始本能键的上升沿；按住或 SparkAssist 切换模式不会重复触发，眩晕时所有按键本就被锁。
     */
    static boolean startsFlash(boolean disrupted, boolean stunned, boolean wasInstinctPressed, boolean instinctPressed) {
        return disrupted && !stunned && instinctPressed && !wasInstinctPressed;
    }

    static int tickFlash(int flashTicks) {
        return Math.max(0, flashTicks - 1);
    }

    /** Alternates every few ticks, starting bright. / 每隔数刻交替一次，以亮色开始。 */
    static int disruptColor(int flashTicks) {
        if (flashTicks <= 0) {
            return DISRUPT_COLOR;
        }
        return ((flashTicks - 1) / FLASH_PERIOD_TICKS) % 2 == 0 ? FLASH_COLOR : DISRUPT_COLOR;
    }

    /** Top-to-bottom lines: stun, disrupt, then the blocked hint during a flash. / 自上而下：眩晕、干扰，闪烁期间再加提示。 */
    static List<Line> lines(int stunTicks, int disruptTicks, int flashTicks) {
        List<Line> lines = new ArrayList<>(3);
        boolean stunned = stunTicks > 0;
        if (stunned) {
            lines.add(new Line(STUNNED_KEY, seconds(stunTicks), STUN_COLOR));
        }
        if (disruptTicks > 0) {
            boolean flashing = !stunned && flashTicks > 0;
            lines.add(new Line(DISRUPTED_KEY, seconds(disruptTicks), flashing ? disruptColor(flashTicks) : DISRUPT_COLOR));
            if (flashing) {
                lines.add(new Line(INSTINCT_BLOCKED_KEY, -1, BLOCKED_COLOR));
            }
        }
        return lines;
    }

    /** Top y of line {@code index} in a bottom-anchored block of {@code count} lines. / 底部对齐的文本块中第 index 行的顶部 y。 */
    static int lineY(int screenHeight, int fontHeight, int index, int count) {
        return screenHeight - ACTION_BAR_CLEARANCE - (count - index) * (fontHeight + LINE_GAP);
    }

    /** One HUD line; {@code seconds < 0} means the key takes no argument. / 一行 HUD；seconds 小于 0 表示该键不带参数。 */
    record Line(String key, int seconds, int color) {
        boolean hasSeconds() {
            return seconds >= 0;
        }
    }
}
