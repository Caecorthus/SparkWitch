package dev.caecorthus.sparkwitch.client.criminalreasoner;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintHudLayoutRules;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Criminal Reasoner's three owner-only bottom-right rows, bottom-up: invisibility
 * (charges hint, or the countdown of a timed invisibility), reasoning progress, and the reasoning key hint (or the
 * reasoning cooldown). Inputs are the local player's own synced state only, so the rows never leak another player and
 * never decide anything. The rows sit in the corner {@link SaintHudLayoutRules} reserves three rows high for the role
 * (Koko / {@link FiendDashHudRules} layout: 5 px padding, all rows measured together and lifted above the hotbar
 * column at narrow GUI widths). No Minecraft client types.
 * 犯罪推理学家右下角三行仅本人可见内容的纯展示规则，自下而上：隐身（充能提示，或限时隐身的倒计时）、推理进度、推理按键
 * 提示（或推理冷却）。输入只有本地玩家自己的同步状态，因此不会泄露其他玩家，也不做任何判定。三行位于
 * {@link SaintHudLayoutRules} 为本职业预留的三行高角落（沿用可可 / {@link FiendDashHudRules} 布局：5 像素边距、三行一起测量，
 * GUI 较窄时一同抬升到快捷栏列之上）。不含 Minecraft 客户端类型。
 */
public final class CriminalReasonerHudRules {
    public static final String COOLDOWN_KEY = "hud.sparkwitch.criminal_reasoner.cooldown";
    public static final String PRESS_KEY = "hud.sparkwitch.criminal_reasoner.press_key";
    public static final String PROGRESS_KEY = "hud.sparkwitch.criminal_reasoner.progress";
    public static final String INVISIBILITY_KEY = "hud.sparkwitch.criminal_reasoner.invisibility";
    public static final String INVISIBILITY_ACTIVE_KEY = "hud.sparkwitch.criminal_reasoner.invisibility_active";
    /** Vanilla Formatting colours the rows always used. / 各行一直使用的原版 Formatting 颜色。 */
    public static final int GRAY = 0xFFAAAAAA;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GOLD = 0xFFFFAA00;
    public static final int AQUA = 0xFF55FFFF;
    public static final int ROW_COUNT = 3;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;

    private CriminalReasonerHudRules() {
    }

    /** What a row's translation arguments are. / 一行的翻译参数是什么。 */
    public enum Argument {
        /** The primary ability key. / 主技能按键。 */
        KEY_HINT,
        /** A countdown in whole seconds. / 整秒倒计时。 */
        SECONDS,
        /** Solved / required. / 已解出 / 所需。 */
        PROGRESS,
        /** The secondary ability key, then the charges left. / 第二技能按键，然后是剩余次数。 */
        CHARGES
    }

    /** A row's translation key, argument kind, numbers and colour. / 一行的翻译键、参数类别、数值与颜色。 */
    public record Line(String key, Argument argument, int value, int total, int color) {
    }

    /**
     * The rows need an acting Reasoner ({@code CriminalReasonerClientModule.isActiveReasoner}: confirmed server, exact
     * role, ACTIVE round rather than STARTING, playing and alive, not spectating, not swallowed; Wathe keeps a dead
     * player's role until the round finalizes), F1 off, Wathe's round-start fade over and Wathe's train HUD shown
     * (Koko precedent). An open screen such as chat does not hide them, like the other corner HUDs.
     * 三行需要可行动的推理学家（{@code CriminalReasonerClientModule.isActiveReasoner}：已确认服务器、精确职业、ACTIVE 回合而非
     * STARTING、参与且存活、非旁观、未被吞下；Wathe 在回合结算前保留死亡玩家的职业）、未按 F1、Wathe 开局淡入结束且 Wathe
     * 列车 HUD 显示（参照可可）。打开聊天等界面不会隐藏它们，与其他角落 HUD 一致。
     */
    public static boolean showsHud(boolean hudHidden, int fade, boolean trainHudActive, boolean activeReasoner) {
        return !hudHidden && fade <= 0 && trainHudActive && activeReasoner;
    }

    /** Top row: the cooldown while it runs, else the key hint. / 最上行：冷却中显示冷却，否则显示按键提示。 */
    public static Line reasoningLine(int cooldownSeconds) {
        return cooldownSeconds > 0
                ? new Line(COOLDOWN_KEY, Argument.SECONDS, cooldownSeconds, 0, GRAY)
                : new Line(PRESS_KEY, Argument.KEY_HINT, 0, 0, WHITE);
    }

    /** Middle row: reasoning progress. / 中间行：推理进度。 */
    public static Line progressLine(int solved, int required) {
        return new Line(PROGRESS_KEY, Argument.PROGRESS, Math.max(0, solved), Math.max(0, required), GOLD);
    }

    /**
     * Bottom row: the countdown, rounded up, while a timed invisibility runs, else the charges hint. An infinite
     * effect has a negative duration ({@code StatusEffectInstance.INFINITE}) and shows the charges hint.
     * 最下行：限时隐身期间显示向上取整的倒计时，否则显示充能提示。无限时长的效果持续时间为负
     * （{@code StatusEffectInstance.INFINITE}），显示充能提示。
     */
    public static Line invisibilityLine(int invisibilityTicks, int charges) {
        return invisibilityTicks > 0
                ? new Line(INVISIBILITY_ACTIVE_KEY, Argument.SECONDS, TickTime.ceilSeconds(invisibilityTicks), 0, AQUA)
                : new Line(INVISIBILITY_KEY, Argument.CHARGES, Math.max(0, charges), 0, GRAY);
    }

    /**
     * The number a row is measured with: countdowns at {@link FiendDashHudRules#MEASURE_SECONDS} or more, so the lift
     * does not flip as a digit goes.
     * 测量行宽所用的数值：倒计时按不少于 {@link FiendDashHudRules#MEASURE_SECONDS} 测量，使抬升判断不会因位数变化而跳动。
     */
    public static int measuredValue(Line line) {
        return line.argument() == Argument.SECONDS ? FiendDashHudRules.measuredSeconds(line.value()) : line.value();
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the bottom row, from the widest measured row. In place the three rows fill exactly the corner
     * {@link SaintHudLayoutRules} reserves; lifted above the hotbar column they also stay above the Saint's Karma
     * line, which keeps the reserved corner.
     * 最下行的顶部 y，按测量最宽的一行计算。未抬升时三行恰好填满 {@link SaintHudLayoutRules} 预留的角落；抬升到快捷栏列之上时
     * 也保持在圣徒业障行之上（业障行仍位于预留角落）。
     */
    public static int bottomRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight) {
        int y = FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, false);
        if (!FiendDashHudRules.overlapsHotbar(scaledWidth, rowX(scaledWidth, measuredWidth))) {
            return y;
        }
        int karmaTop = SaintHudLayoutRules.drawY(scaledHeight, fontHeight,
                SaintHudLayoutRules.reservedBottomHeight(CriminalReasonerRules.ROLE_ID, fontHeight, false));
        return Math.min(y, karmaTop - ROW_GAP - fontHeight);
    }

    /** Top y of a row; row 0 is the bottom one. / 某一行的顶部 y；第 0 行在最下方。 */
    public static int rowY(int bottomRowY, int fontHeight, int row) {
        return bottomRowY - row * (fontHeight + ROW_GAP);
    }
}
