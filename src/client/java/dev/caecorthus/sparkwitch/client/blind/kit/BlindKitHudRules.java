package dev.caecorthus.sparkwitch.client.blind.kit;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Pure presentation rules of the Blind's owner-only HUD: the cane and Attune states (ready / active Ns / cooldown Ns)
 * and whether the ComTac VIII is worn. Text only and only the local player's own state, so it can never leak another
 * player. Right-aligned in the bottom-right corner with the shared skill line's 5 px padding, Attune on the bottom row
 * (the Blind has no witch skill, so that row is normally free; if it is ever occupied the block moves up one line),
 * stacked upward with a 2 px gap. When the widest line would reach the centred hotbar column (narrow GUI widths), the
 * whole block is lifted above the hotbar, Wathe's stamina row and its cooldown number ({@code SeekerHudRules}
 * precedent). Role-owned presentation; never the witch skill inventory panel.
 * 盲人仅本人可见 HUD 的纯展示规则：盲杖与凝神状态（就绪 / 持续 N 秒 / 冷却 N 秒）以及是否戴着 ComTac VIII。只有文字，
 * 且只读本地玩家自己的状态，因此绝不会泄露其他玩家。在右下角右对齐，边距与共享技能行相同（5 像素），凝神位于最底行
 * （盲人没有魔女技能，该行通常空闲；若被占用则整体上移一行），自下而上堆叠，行距 2 像素。最宽的一行会伸入居中快捷栏列时
 * （GUI 较窄），整块抬升到快捷栏、Wathe 体力行及其冷却数字之上（参照 {@code SeekerHudRules}）。属于职业自有展示，
 * 从不使用魔女技能背包面板。
 */
public final class BlindKitHudRules {
    public static final String ATTUNE_NAME_KEY = "hud.sparkwitch.blind.attune.name";
    public static final String CANE_NAME_KEY = "item.sparkwitch.white_cane";
    public static final String ATTUNE_READY_KEY = "hud.sparkwitch.blind.attune.ready";
    public static final String ATTUNE_ACTIVE_KEY = "hud.sparkwitch.blind.attune.active";
    public static final String ATTUNE_COOLDOWN_KEY = "hud.sparkwitch.blind.attune.cooldown";
    public static final String CANE_READY_KEY = "hud.sparkwitch.blind.cane.ready";
    public static final String CANE_ACTIVE_KEY = "hud.sparkwitch.blind.cane.active";
    public static final String CANE_COOLDOWN_KEY = "hud.sparkwitch.blind.cane.cooldown";
    public static final String COMTAC_WORN_KEY = "hud.sparkwitch.blind.comtac.worn";
    public static final String COMTAC_NOT_WORN_KEY = "hud.sparkwitch.blind.comtac.not_worn";
    public static final int READY_COLOR = 0xFF000000 | BlindRules.COLOR;
    public static final int ACTIVE_COLOR = 0xFFFFFFFF;
    public static final int COOLDOWN_COLOR = 0xFF8C99A6;
    public static final int RIGHT_PADDING = 5;
    public static final int BOTTOM_PADDING = 5;
    public static final int ROW_GAP = 2;
    /** Half the vanilla hotbar width (182 / 2). / 原版快捷栏宽度的一半（182 / 2）。 */
    public static final int HOTBAR_HALF_WIDTH = 91;
    /** Horizontal gap kept from the hotbar. / 与快捷栏保持的水平间距。 */
    public static final int HOTBAR_GAP = 4;
    /**
     * Bottom offset of the lifted block: above Wathe's stamina row (top at height - 39) and its cooldown number, + 2 px.
     * 抬升后文本块的底部偏移：位于 Wathe 体力行（顶端为 height - 39）及其冷却数字之上，再留 2 像素。
     */
    public static final int LIFTED_BOTTOM_OFFSET = 41;
    /**
     * Seconds value used only to measure a line, so the lift decision does not flip as a countdown loses a digit.
     * 仅用于测量行宽的秒数，使抬升判断不会因倒计时少一位数字而来回跳动。
     */
    public static final int MEASURE_SECONDS = 99;

    private BlindKitHudRules() {
    }

    /** ComTac state shown on the HUD. / HUD 上显示的 ComTac 状态。 */
    public enum ComTac {
        NONE,
        WORN,
        NOT_WORN
    }

    /**
     * One HUD line. Arguments are, in order: the translated {@code nameKey} (if any), the ability key (if
     * {@code keyHint}), then {@code seconds} (if not negative).
     * 一行 HUD。参数依次为：翻译后的 nameKey（若有）、技能按键（若 keyHint）、seconds（若非负）。
     */
    public record Line(String key, @Nullable String nameKey, boolean keyHint, int seconds, int color) {
        public boolean hasSeconds() {
            return seconds >= 0;
        }
    }

    /**
     * Drawn only on the local Blind's own active view, in an ACTIVE round once Wathe's round-start fade is over
     * (STOPPING and the fade are Wathe's black overlay, which this TAIL-drawn HUD would sit on), with Wathe's train HUD
     * shown and F1 off, and never while a Taotie has swallowed the Blind (neither the cane nor Attune can be used then,
     * so no "ready" hint is shown).
     * 仅在本地盲人自身视图激活、回合为 ACTIVE 且 Wathe 开局淡入结束（STOPPING 与淡入都是 Wathe 的黑幕，而本 HUD 在 TAIL
     * 绘制会浮在其上）、Wathe 列车 HUD 显示且未按 F1 时绘制；被饕餮吞下时从不绘制（此时盲杖与凝神都不可用，
     * 因此不显示“就绪”提示）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean blindViewActive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && blindViewActive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    /**
     * Top-to-bottom lines: ComTac (when owned), cane, Attune. A cooldown includes its active window, so the active
     * state is checked first.
     * 自上而下：ComTac（持有时）、盲杖、凝神。冷却包含持续窗口，因此先判断持续状态。
     */
    public static List<Line> lines(int caneCooldownTicks, int caneActiveTicks, int attuneCooldownTicks,
                                   int attuneActiveTicks, ComTac comTac) {
        List<Line> lines = new ArrayList<>(3);
        if (comTac == ComTac.WORN) {
            lines.add(new Line(COMTAC_WORN_KEY, null, false, -1, ACTIVE_COLOR));
        } else if (comTac == ComTac.NOT_WORN) {
            lines.add(new Line(COMTAC_NOT_WORN_KEY, null, false, -1, COOLDOWN_COLOR));
        }
        lines.add(skillLine(CANE_NAME_KEY, CANE_READY_KEY, CANE_ACTIVE_KEY, CANE_COOLDOWN_KEY, false,
                caneCooldownTicks, caneActiveTicks));
        lines.add(skillLine(ATTUNE_NAME_KEY, ATTUNE_READY_KEY, ATTUNE_ACTIVE_KEY, ATTUNE_COOLDOWN_KEY, true,
                attuneCooldownTicks, attuneActiveTicks));
        return lines;
    }

    /** Left x of a right-aligned line. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return Math.max(0, scaledWidth - RIGHT_PADDING - Math.max(0, textWidth));
    }

    /**
     * Whether a line starting at {@code lineX} would reach into the centred hotbar column (hotbar, stamina row,
     * cooldown number).
     * 从 {@code lineX} 开始的行是否会伸入居中的快捷栏列（快捷栏、体力行、冷却数字）。
     */
    public static boolean overlapsHotbar(int scaledWidth, int lineX) {
        return lineX < scaledWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
    }

    /**
     * Distance from the screen bottom to the bottom of the block: lifted above the hotbar column when the widest line
     * would reach it, else the 5 px padding plus one line when the shared bottom-right skill line is occupied.
     * 文本块底部到屏幕底部的距离：最宽的一行会伸入快捷栏列时抬升到其上方，否则为 5 像素边距，共享右下角技能行被占用时
     * 再加一行。
     */
    public static int bottomOffset(int scaledWidth, int widestLine, int fontHeight, boolean sharedLineOccupied) {
        if (overlapsHotbar(scaledWidth, rowX(scaledWidth, widestLine))) {
            return LIFTED_BOTTOM_OFFSET;
        }
        return BOTTOM_PADDING + (sharedLineOccupied ? fontHeight + ROW_GAP : 0);
    }

    /**
     * Top y of line {@code index} (0 = top) in a block of {@code count} lines whose bottom sits {@code bottomOffset}
     * above the screen bottom.
     * 底部距屏幕底部 bottomOffset、共 count 行的文本块中第 index 行（0 为顶行）的顶部 y。
     */
    public static int rowY(int scaledHeight, int fontHeight, int index, int count, int bottomOffset) {
        int step = fontHeight + ROW_GAP;
        int bottomRow = scaledHeight - bottomOffset - fontHeight;
        return bottomRow - (count - 1 - index) * step;
    }

    /** The seconds a line is measured with (see {@link #MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return seconds < 0 ? seconds : Math.max(seconds, MEASURE_SECONDS);
    }

    private static Line skillLine(String nameKey, String readyKey, String activeKey, String cooldownKey,
                                  boolean keyHint, int cooldownTicks, int activeTicks) {
        if (activeTicks > 0) {
            return new Line(activeKey, nameKey, false, seconds(activeTicks), ACTIVE_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(cooldownKey, nameKey, false, seconds(cooldownTicks), COOLDOWN_COLOR);
        }
        return new Line(readyKey, nameKey, keyHint, -1, READY_COLOR);
    }
}
