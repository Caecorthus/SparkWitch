package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumWindowKind;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Medium's two bottom-right rows (owner pick 2026-11-13): 通灵 above, 幽灵的加护
 * below, each in the first state that applies: its own window running (teal), its cooldown (grey), another window
 * blocking it (grey), short of coins (grey), ready with its key (teal). The rows sit in the shared SparkWitch corner
 * slot that {@code SaintHudLayoutRules} reserves two rows high and lift together above the hotbar column at narrow GUI
 * widths ({@link FiendDashHudRules#rowY}, measured with 99 s). 招魂 has no corner row: it lives in the inventory.
 * 通灵师右下角两行的纯展示规则（所有者 2026-11-13 选定）：上行通灵，下行幽灵的加护，各取第一个成立的状态：自己的窗口
 * 进行中（青绿）、冷却中（灰）、被其他窗口占用（灰）、金币不足（灰）、带按键的可用（青绿）。两行位于 {@code SaintHudLayoutRules}
 * 预留两行高度的 SparkWitch 共享右下角位置，GUI 较窄时一同抬升到快捷栏列之上（{@link FiendDashHudRules#rowY}，按 99 秒测宽）。
 * 招魂没有角落行：它位于背包中。
 */
public final class MediumHudRules {
    public static final int READY_COLOR = 0xFF000000 | MediumRules.COLOR;
    /** The Fiend / Charon / Athlete cooldown grey. / 魔人、卡戎、运动员冷却行的灰色。 */
    public static final int MUTED_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    public static final String BUSY_KEY = "hud.sparkwitch.medium.window.busy";
    /** A chip's icon is the 9x9 blessing icon. / 图标为 9x9 的加护图标。 */
    public static final int CHIP_ICON = 9;
    /** Gap between two active-blessing chips. / 两个生效加护图标之间的间隔。 */
    public static final int CHIP_GAP = 4;
    /** Icon to its seconds label. / 图标与秒数之间的间隔。 */
    public static final int CHIP_LABEL_GAP = 2;
    /** The chip strip to the lower row's text. / 图标串与下行文字之间的间隔。 */
    public static final int CHIPS_TO_TEXT = 6;

    private MediumHudRules() {
    }

    /** The two skills with a corner row and the window kind each one opens. / 拥有角落行的两个技能及其开启的窗口种类。 */
    public enum Skill {
        SEANCE("seance", MediumWindowKind.QUESTION),
        BLESSING("blessing", MediumWindowKind.BLESSING);

        private final String path;
        private final MediumWindowKind kind;

        Skill(String path, MediumWindowKind kind) {
            this.path = path;
            this.kind = kind;
        }

        String key(String state) {
            return "hud.sparkwitch.medium." + path + "." + state;
        }

        public MediumWindowKind kind() {
            return kind;
        }
    }

    /** What the single argument of a row's lang key is. / 行语言键唯一参数的类型。 */
    public enum Arg {
        SECONDS,
        PRICE,
        KEY
    }

    /** One row: lang key, argument kind and value (seconds or coins; unused for KEY), colour. / 一行：语言键、参数类型与值、颜色。 */
    public record Line(String key, Arg arg, int value, int color) {
        /**
         * The value the row is measured with for the lift decision: seconds as at least 99 so the rows do not jump as a
         * countdown loses a digit.
         * 抬升判定时测量所用的值：秒数至少按 99 计，倒计时少一位时两行不会跳动。
         */
        public int measureValue() {
            return arg == Arg.SECONDS ? FiendDashHudRules.measuredSeconds(value) : value;
        }
    }

    /**
     * The row of {@code skill}. {@code windowRevealed} is the world window's {@code isRevealed()}: on a living
     * Medium's client it means "my own window", because the content goes only to voters and the caster.
     * {@code skill} 的行。{@code windowRevealed} 即世界窗口的 {@code isRevealed()}：在存活通灵师的客户端上它表示「我自己的
     * 窗口」，因为内容只发给投票者与施放者。
     */
    public static Line row(Skill skill, int cooldownTicks, boolean windowActive, boolean windowRevealed,
                           MediumWindowKind windowKind, int windowTicks, int balance, int price) {
        if (windowActive && windowRevealed && windowKind == skill.kind()) {
            return new Line(skill.key("active"), Arg.SECONDS, TickTime.ceilSeconds(windowTicks), READY_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(skill.key("cooldown"), Arg.SECONDS, TickTime.ceilSeconds(cooldownTicks), MUTED_COLOR);
        }
        if (windowActive) {
            // Only one window exists in the world: any live one blocks both skills. / 世界上只有一个窗口：任何进行中的窗口都会阻止两个技能。
            return new Line(BUSY_KEY, Arg.SECONDS, TickTime.ceilSeconds(windowTicks), MUTED_COLOR);
        }
        if (balance < price) {
            return new Line(skill.key("price"), Arg.PRICE, price, MUTED_COLOR);
        }
        return new Line(skill.key("ready"), Arg.KEY, 0, READY_COLOR);
    }

    /**
     * Width of the chip strip: each chip is a 9 px icon, plus {@link #CHIP_LABEL_GAP} and its label when the label
     * width is above 0, with {@link #CHIP_GAP} between chips. Zero chips are 0 px wide.
     * 图标串宽度：每个图标 9 像素，标签宽度大于 0 时再加 {@link #CHIP_LABEL_GAP} 与标签，图标之间隔 {@link #CHIP_GAP}。
     * 没有图标时宽度为 0。
     */
    public static int chipStripWidth(int... labelWidths) {
        int width = 0;
        for (int i = 0; i < labelWidths.length; i++) {
            width += CHIP_ICON + (labelWidths[i] > 0 ? CHIP_LABEL_GAP + labelWidths[i] : 0);
            if (i > 0) {
                width += CHIP_GAP;
            }
        }
        return width;
    }
}
