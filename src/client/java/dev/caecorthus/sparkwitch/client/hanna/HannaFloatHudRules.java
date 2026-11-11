package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of Hanna's owner-only float line, in priority order: "floating Ns", "cooling down Ns",
 * "needs N mana", "ready: press G". Inputs are the local player's own synced state only (her float ticks, her ready
 * tick against the client world clock, her mana), so the line never leaks another player and never decides anything:
 * the server alone starts a float. It takes the Fiend Dash line's bottom-right layout as is, like Noa's painting line
 * (5 px padding, one row up over an occupied shared skill line, lifted above the hotbar column at narrow GUI widths);
 * a player has one role, so the Fiend, Athlete and Noa lines never meet hers, and her mana stays in the shared top-right
 * mana row. Role-owned presentation; never the witch skill inventory panel and never the action bar. No Minecraft
 * client types, so it is unit-testable.
 * 汉娜仅本人可见的浮游行的纯展示规则，按优先级依次为：“浮游中 N 秒”“冷却 N 秒”“需要 N 魔力”“就绪：按 G”。输入只有本地玩家
 * 自己的同步状态（浮游剩余刻、依据客户端世界时钟的就绪刻、魔力），因此该行不会泄露其他玩家，也不做任何判定：浮游是否开始只由
 * 服务端决定。与诺亚作画行一样直接沿用魔人疾驰行的右下角布局（5 像素边距、共享技能行被占用时上移一行、GUI 较窄时抬升到
 * 快捷栏列之上）；一名玩家只有一个职业，魔人、运动员与诺亚的行不会与她的行同时出现，她的魔力仍显示在右上角的共享魔力行。
 * 属于职业自有展示，从不使用魔女技能背包面板，也从不使用动作栏。不含 Minecraft 客户端类型，可做单元测试。
 */
public final class HannaFloatHudRules {
    public static final String ACTIVE_KEY = "hud.sparkwitch.hanna.float.active";
    public static final String COOLDOWN_KEY = "hud.sparkwitch.hanna.float.cooldown";
    public static final String NO_MANA_KEY = "hud.sparkwitch.hanna.float.no_mana";
    public static final String READY_KEY = "hud.sparkwitch.hanna.float.ready";
    /** Opaque Hanna identity colour (ready and floating). / 不透明的汉娜身份色（就绪与浮游中）。 */
    public static final int READY_COLOR = 0xFF000000 | HannaRules.COLOR;
    /**
     * Same muted grey as the Noa, Blind, Fiend and Athlete cooldown lines; also the "not enough mana" line, which is
     * equally unusable.
     * 与诺亚、盲人、魔人、运动员冷却行相同的灰色；“魔力不足”行同样不可用，也用此色。
     */
    public static final int COOLDOWN_COLOR = FiendDashHudRules.COOLDOWN_COLOR;

    private HannaFloatHudRules() {
    }

    /** What the line's single translation argument is. / 该行唯一的翻译参数是什么。 */
    public enum Argument {
        /** The ability key's display text. / 技能按键的显示文本。 */
        KEY_HINT,
        /** A countdown in whole seconds. / 整秒倒计时。 */
        SECONDS,
        /** A fixed mana amount. / 固定的魔力数值。 */
        MANA
    }

    /**
     * The line's translation key, the kind of its single argument, that argument's number (unused for the key hint)
     * and colour.
     * 该行的翻译键、唯一参数的类别、该参数的数值（按键提示不使用）与颜色。
     */
    public record Line(String key, Argument argument, int value, int color) {
        public boolean keyHint() {
            return argument == Argument.KEY_HINT;
        }
    }

    /**
     * Drawn only on Hanna's own client while she is playing and alive (so never as a spectator), in an ACTIVE round once
     * Wathe's round-start fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie has swallowed
     * her (the server refuses a swallowed float; no "ready: press G" while she cannot float).
     * 仅在汉娜自己的客户端上、其参与且存活（因此旁观时从不绘制）、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD
     * 显示且未按 F1 时绘制；被饕餮吞下时从不绘制（服务端拒绝被吞下时的浮游；无法浮游时不显示“就绪：按 G”）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean hanna, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && hanna && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return TickTime.ceilSeconds(ticks);
    }

    /**
     * The line, in priority order: floating (the cooldown already runs behind a float, so the float is asked first),
     * cooling down, ready without {@link HannaRules#FLOAT_MANA} mana (a player without the mana system has none), ready.
     * 该行，按优先级：浮游中（浮游期间冷却已在其后计时，因此先判断浮游）、冷却中、就绪但魔力不足
     * {@link HannaRules#FLOAT_MANA}（未启用魔力系统视为没有魔力）、就绪。
     */
    public static Line line(int floatTicks, int cooldownTicks, boolean hasManaSystem, int mana) {
        if (floatTicks > 0) {
            return new Line(ACTIVE_KEY, Argument.SECONDS, seconds(floatTicks), READY_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(COOLDOWN_KEY, Argument.SECONDS, seconds(cooldownTicks), COOLDOWN_COLOR);
        }
        if (!hasManaSystem || mana < HannaRules.FLOAT_MANA) {
            return new Line(NO_MANA_KEY, Argument.MANA, HannaRules.FLOAT_MANA, COOLDOWN_COLOR);
        }
        return new Line(READY_KEY, Argument.KEY_HINT, 0, READY_COLOR);
    }

    /** Left x of a right-aligned line. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the line, with the shared corner layout (see {@link FiendDashHudRules#rowY}).
     * 该行的顶部 y，使用共享的角落布局（见 {@link FiendDashHudRules#rowY}）。
     */
    public static int rowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                           boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /**
     * The number the line is measured with: a countdown is measured at {@link FiendDashHudRules#MEASURE_SECONDS} or
     * more so the lift decision does not flip as it loses a digit; any other number is measured as shown.
     * 测量行宽所用的数值：倒计时按不少于 {@link FiendDashHudRules#MEASURE_SECONDS} 测量，使抬升判断不会因少一位数字而来回
     * 跳动；其他数值按实际显示测量。
     */
    public static int measuredValue(Line line) {
        return line.argument() == Argument.SECONDS ? FiendDashHudRules.measuredSeconds(line.value()) : line.value();
    }
}
