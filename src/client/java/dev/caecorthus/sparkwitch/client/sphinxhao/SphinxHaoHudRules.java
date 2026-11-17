package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Sphinx Hao's four owner-only rows in the bottom-right corner, bottom to top:
 * <ol>
 *   <li>嘉豪病毒 (ability key): "ready: press G" or "cooling down N s";</li>
 *   <li>豪气冲天 (secondary key): "陆泳中 N s" while it crawls in a window, else "cooling down N s", else
 *   "ready: press N";</li>
 *   <li>the infected count (the synced infected set, which only a Sphinx Hao, a spectator or a creative player gets);</li>
 *   <li>its 豪度值 (hao).</li>
 * </ol>
 * Inputs are the local player's own synced state only (the owner-only {@code sparkwitch:sphinx_hao_player} ready ticks
 * and hao, the world window and infected set against the client world clock), so the rows never decide anything: the
 * server alone infects, opens windows and pays hao. They take the Koko/Fiend Dash corner layout (5 px padding, one row
 * up over an occupied shared skill line or the Saint's Karma line, lifted above the hotbar column at narrow GUI widths,
 * every row measured together). Role-owned presentation: never the action bar and never the {@code gui.sparkwitch.skills}
 * panel. No Minecraft client types, so it is unit-testable.
 * 狮身豪右下角四行仅本人可见内容的纯展示规则，自下而上：
 * <ol>
 *   <li>嘉豪病毒（技能键）：“就绪：按 G”或“冷却 N 秒”；</li>
 *   <li>豪气冲天（第二技能键）：窗口中自己陆泳时为“陆泳中 N 秒”，否则“冷却 N 秒”，否则“就绪：按 N”；</li>
 *   <li>感染人数（同步的感染集合，只有狮身豪、旁观者与创造模式玩家会收到）；</li>
 *   <li>豪度值。</li>
 * </ol>
 * 输入只有本地玩家自己的同步状态（只同步给本人的 {@code sparkwitch:sphinx_hao_player} 就绪刻与豪度值，以及依据客户端世界时钟的
 * 世界窗口与感染集合），因此这些行不做任何判定：感染、开启窗口与支付豪度值只由服务端决定。沿用可可/魔人疾驰行的角落布局（5 像素
 * 边距、共享技能行或圣徒业报行被占用时上移一行、GUI 较窄时抬升到快捷栏列之上，所有行一起测量）。属于职业自有展示：从不使用动作栏，
 * 也从不进入 {@code gui.sparkwitch.skills} 面板。不含 Minecraft 客户端类型，可做单元测试。
 */
public final class SphinxHaoHudRules {
    public static final String INFECT_READY_KEY = "hud.sparkwitch.sphinx_hao.infect.ready";
    public static final String INFECT_COOLDOWN_KEY = "hud.sparkwitch.sphinx_hao.infect.cooldown";
    public static final String ROAR_READY_KEY = "hud.sparkwitch.sphinx_hao.roar.ready";
    public static final String ROAR_COOLDOWN_KEY = "hud.sparkwitch.sphinx_hao.roar.cooldown";
    public static final String ROAR_ACTIVE_KEY = "hud.sparkwitch.sphinx_hao.roar.active";
    public static final String INFECTED_KEY = "hud.sparkwitch.sphinx_hao.infected";
    public static final String HAO_KEY = "hud.sparkwitch.sphinx_hao.hao";
    /** Opaque Sphinx Hao coral (ready, crawling and hao). / 不透明的狮身豪珊瑚色（就绪、陆泳中与豪度值）。 */
    public static final int READY_COLOR = 0xFF000000 | SphinxHaoRules.COLOR;
    /** Opaque Team Jiahao gold, the infected outline's colour. / 不透明的嘉豪阵营金色，与感染者描边同色。 */
    public static final int INFECTED_COLOR = 0xFF000000 | SphinxHaoInstinctRules.OUTLINE_COLOR;
    /** The muted grey of the other roles' cooldown lines. / 与其他职业冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    /** Hao is measured at three digits and the count at two, so the rows do not shift. / 豪度值按三位、人数按两位测量。 */
    public static final int MEASURE_HAO = 999;
    public static final int MEASURE_INFECTED = 99;
    public static final int ROW_GAP = 2;
    /** Rows drawn, bottom to top: infect, roar, infected, hao. / 绘制的行数，自下而上：感染、豪气冲天、感染人数、豪度值。 */
    public static final int ROWS = 4;

    private SphinxHaoHudRules() {
    }

    /** What the line's single translation argument is. / 该行唯一的翻译参数是什么。 */
    public enum Argument {
        /** The ability key's display text. / 技能按键的显示文本。 */
        ABILITY_KEY,
        /** The secondary skill key's display text. / 第二技能按键的显示文本。 */
        SECONDARY_KEY,
        /** A countdown in whole seconds. / 整秒倒计时。 */
        SECONDS,
        /** A count of players. / 玩家人数。 */
        COUNT,
        /** Its hao. / 豪度值。 */
        HAO
    }

    /** A row's translation key, argument kind, number (unused for key hints) and colour. / 一行的翻译键、参数类别、数值与颜色。 */
    public record Line(String key, Argument argument, int value, int color) {
        public boolean keyHint() {
            return argument == Argument.ABILITY_KEY || argument == Argument.SECONDARY_KEY;
        }
    }

    /**
     * Drawn only on the Sphinx Hao's own client while it is playing and alive (never as a spectator), in an ACTIVE
     * round once Wathe's round-start fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie
     * has swallowed it.
     * 仅在狮身豪自己的客户端上、其参与且存活（旁观时从不绘制）、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD 显示且未按 F1
     * 时绘制；被饕餮吞下时从不绘制。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean sphinxHao, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && sphinxHao && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return TickTime.ceilSeconds(ticks);
    }

    /** 嘉豪病毒: cooling down, else ready. / 嘉豪病毒：冷却中，否则就绪。 */
    public static Line infectLine(int cooldownTicks) {
        if (cooldownTicks > 0) {
            return new Line(INFECT_COOLDOWN_KEY, Argument.SECONDS, seconds(cooldownTicks), COOLDOWN_COLOR);
        }
        return new Line(INFECT_READY_KEY, Argument.ABILITY_KEY, 0, READY_COLOR);
    }

    /**
     * 豪气冲天, in priority order: crawling in a window (its cooldown already runs behind the window, so the window is
     * asked first), cooling down, ready.
     * 豪气冲天，按优先级：窗口中陆泳（窗口期间冷却已在其后计时，因此先判断窗口）、冷却中、就绪。
     */
    public static Line roarLine(int crawlTicks, int cooldownTicks) {
        if (crawlTicks > 0) {
            return new Line(ROAR_ACTIVE_KEY, Argument.SECONDS, seconds(crawlTicks), READY_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(ROAR_COOLDOWN_KEY, Argument.SECONDS, seconds(cooldownTicks), COOLDOWN_COLOR);
        }
        return new Line(ROAR_READY_KEY, Argument.SECONDARY_KEY, 0, READY_COLOR);
    }

    /** "Infected: N". / “已感染 N 人”。 */
    public static Line infectedLine(int infected) {
        return new Line(INFECTED_KEY, Argument.COUNT, Math.max(0, infected), INFECTED_COLOR);
    }

    /** "Hao N". / “豪度值 N”。 */
    public static Line haoLine(int hao) {
        return new Line(HAO_KEY, Argument.HAO, Math.max(0, hao), READY_COLOR);
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the bottom row (嘉豪病毒), with the shared corner layout ({@link FiendDashHudRules#rowY}); the widest
     * measured row decides the lift for all four.
     * 最下一行（嘉豪病毒）的顶部 y，使用共享角落布局（{@link FiendDashHudRules#rowY}）；四行中测量最宽的一行决定全部是否抬升。
     */
    public static int bottomRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                                 boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /** Top y of row {@code index} (0 = bottom) above the bottom row. / 位于最下一行之上第 {@code index} 行（0 为最下）的顶部 y。 */
    public static int rowY(int bottomRowY, int fontHeight, int index) {
        return bottomRowY - Math.max(0, index) * (fontHeight + ROW_GAP);
    }

    /**
     * The number a row is measured with: countdowns at {@link FiendDashHudRules#MEASURE_SECONDS} or more, hao at
     * {@link #MEASURE_HAO} or more and the count at {@link #MEASURE_INFECTED} or more, so the lift decision does not
     * flip as a digit comes or goes.
     * 测量行宽所用的数值：倒计时不少于 {@link FiendDashHudRules#MEASURE_SECONDS}、豪度值不少于 {@link #MEASURE_HAO}、人数不少于
     * {@link #MEASURE_INFECTED}，使抬升判断不会因数字位数变化而来回跳动。
     */
    public static int measuredValue(Line line) {
        return switch (line.argument()) {
            case SECONDS -> FiendDashHudRules.measuredSeconds(line.value());
            case HAO -> Math.max(line.value(), MEASURE_HAO);
            case COUNT -> Math.max(line.value(), MEASURE_INFECTED);
            default -> line.value();
        };
    }
}
