package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Charon's owner-only two-row readout: the pardon (shared ability key) on the upper row
 * and the ferry (secondary key) on the lower row, each "ready" with its key or the cooldown in whole seconds, from the
 * cooldowns synced only to that Charon. Both rows sit in the shared bottom-right corner slot with the Fiend Dash line's
 * layout ({@link FiendDashHudRules#rowX}, {@link FiendDashHudRules#rowY}: 5 px padding, lifted above the hotbar column
 * at narrow GUI widths), and {@code SaintHudLayoutRules} reserves both rows for the Charon (like the Specter) so the
 * Saint and Karma lines stack above instead of overlapping. Role-owned presentation; never the witch skill inventory
 * panel and never the action bar.
 * 卡戎仅本人可见的两行读数的纯展示规则：上行为宽恕（共享技能键），下行为摆渡（第二技能键），依据只同步给该卡戎的冷却
 * 显示带按键的“就绪”或向上取整的冷却秒数。两行都位于共享右下角位置，沿用魔人疾驰行的布局（{@link FiendDashHudRules#rowX}、
 * {@link FiendDashHudRules#rowY}：5 像素边距，GUI 较窄时抬升到快捷栏列之上）；{@code SaintHudLayoutRules} 为卡戎预留这两行
 * （与幽魂相同），圣徒与业障行因此叠放在其上方而不会重叠。属于职业自有展示，从不使用魔女技能背包面板，也从不使用动作栏。
 */
public final class FerrymanHudRules {
    public static final String PARDON_READY_KEY = "hud.sparkwitch.ferryman.pardon.ready";
    public static final String PARDON_COOLDOWN_KEY = "hud.sparkwitch.ferryman.pardon.cooldown";
    public static final String FERRY_READY_KEY = "hud.sparkwitch.ferryman.ferry.ready";
    public static final String FERRY_COOLDOWN_KEY = "hud.sparkwitch.ferryman.ferry.cooldown";
    /** Amber, the fresh-body highlight colour, opaque. / 琥珀色（与新鲜遗体高亮相同），不透明。 */
    public static final int READY_COLOR = 0xFFFFD27A;
    /** Same muted grey as the Fiend's, the Athlete's and Noa's cooldown lines. / 与魔人、运动员、诺亚冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;

    private FerrymanHudRules() {
    }

    /**
     * One row: its translation key, its seconds argument (negative = ready, no seconds) and colour. Both rows always
     * carry their key name as the first argument.
     * 一行：翻译键、秒数参数（负数表示就绪、无秒数）与颜色。两行的第一个参数始终是按键名。
     */
    public record Line(String key, int seconds, int color) {
        public boolean hasSeconds() {
            return seconds >= 0;
        }
    }

    /**
     * The view gates on top of {@link FerrymanClient#isActiveLocalCharon} (the Charon's own client, confirmed
     * SparkWitch server, ACTIVE round, playing and alive in the world, so never while swallowed or dead): Wathe's
     * round-start fade is over, Wathe's train HUD is shown and F1 is off.
     * 在 {@link FerrymanClient#isActiveLocalCharon}（卡戎自己的客户端、已确认的 SparkWitch 服务器、ACTIVE 回合、在局存活且
     * 身处世界中，因此被吞下或死亡时从不绘制）之上的视图门槛：Wathe 开局淡入已结束、Wathe 列车 HUD 显示且未按 F1。
     */
    public static boolean showsHud(boolean hudHidden, int fade, boolean trainHudActive) {
        return !hudHidden && fade <= 0 && trainHudActive;
    }

    /** Pardon row: ready, or the remaining cooldown rounded up. / 宽恕行：就绪，或向上取整的剩余冷却。 */
    public static Line pardonLine(int cooldownTicks) {
        return cooldownTicks > 0
                ? new Line(PARDON_COOLDOWN_KEY, TickTime.ceilSeconds(cooldownTicks), COOLDOWN_COLOR)
                : new Line(PARDON_READY_KEY, -1, READY_COLOR);
    }

    /** Ferry row: ready, or the remaining cooldown rounded up. / 摆渡行：就绪，或向上取整的剩余冷却。 */
    public static Line ferryLine(int cooldownTicks) {
        return cooldownTicks > 0
                ? new Line(FERRY_COOLDOWN_KEY, TickTime.ceilSeconds(cooldownTicks), COOLDOWN_COLOR)
                : new Line(FERRY_READY_KEY, -1, READY_COLOR);
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the lower (ferry) row. Both rows lift together: {@code measuredWidth} is the wider of the two rows
     * measured with {@link #measuredSeconds}, and the shared corner row is never occupied by another line because the
     * Charon has no witch skill and the Saint / Karma lines are reserved above.
     * 下行（摆渡）的顶部 y。两行一同抬升：{@code measuredWidth} 是两行按 {@link #measuredSeconds} 测量后的较宽者；卡戎没有
     * 魔女技能，圣徒 / 业障行又预留在上方，因此共享角落行不会被其他行占用。
     */
    public static int lowerRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, false);
    }

    /** Top y of the upper (pardon) row, one row and the gap above the lower one. / 上行（宽恕）的顶部 y：位于下行上方一行加间距。 */
    public static int upperRowY(int lowerRowY, int fontHeight) {
        return lowerRowY - fontHeight - ROW_GAP;
    }

    /** The seconds a row is measured with (see {@link FiendDashHudRules#MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return FiendDashHudRules.measuredSeconds(seconds);
    }
}
