package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of Koko's two owner-only rows in the bottom-right corner. The lower row is her stream line,
 * in priority order: "the stream lasts N more seconds", "the stream is available in N seconds", "the stream needs N
 * mana", "the stream is ready (press G to start)". The upper row is her traffic against the 1500 she needs. Inputs are
 * the local player's own synced state only (her stream and ready ticks against the client world clock, her traffic,
 * her mana), so the rows never leak another player and never decide anything: the server alone starts a stream. They
 * take the Fiend Dash line's corner layout (5 px padding, one row up over an occupied shared skill line, lifted above
 * the hotbar column at narrow GUI widths); her mana stays in the shared top-right mana row. Role-owned presentation;
 * never the witch skill inventory panel and never the action bar. No Minecraft client types, so it is unit-testable.
 * 可可右下角两行仅本人可见内容的纯展示规则。下面一行是直播行，按优先级依次为：“直播状态还能持续 N 秒”“直播将在 N 秒后可用”
 * “直播需要 N 魔力”“直播准备就绪（按下 G 开始）”。上面一行是她的流量与所需的 1500。输入只有本地玩家自己的同步状态（依据
 * 客户端世界时钟的直播刻与就绪刻、流量、魔力），因此这两行不会泄露其他玩家，也不做任何判定：直播是否开始只由服务端决定。
 * 沿用魔人疾驰行的角落布局（5 像素边距、共享技能行被占用时上移一行、GUI 较窄时抬升到快捷栏列之上）；她的魔力仍显示在右上角
 * 的共享魔力行。属于职业自有展示，从不使用魔女技能背包面板，也从不使用动作栏。不含 Minecraft 客户端类型，可做单元测试。
 */
public final class KokoStreamHudRules {
    public static final String ACTIVE_KEY = "hud.sparkwitch.koko.stream.active";
    public static final String COOLDOWN_KEY = "hud.sparkwitch.koko.stream.cooldown";
    public static final String NO_MANA_KEY = "hud.sparkwitch.koko.stream.no_mana";
    public static final String READY_KEY = "hud.sparkwitch.koko.stream.ready";
    public static final String TRAFFIC_KEY = "hud.sparkwitch.koko.traffic";
    /** Opaque Koko identity colour (ready, live and traffic). / 不透明的可可身份色（就绪、直播中与流量）。 */
    public static final int READY_COLOR = 0xFF000000 | KokoRules.COLOR;
    /** The muted grey of the other roles' cooldown lines; also the "needs mana" line. / 与其他职业冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    /** Traffic is measured at four digits, so the row does not shift when it reaches 1000. / 流量按四位数测量。 */
    public static final int MEASURE_TRAFFIC = 1000;
    private static final int ROW_GAP = 2;

    private KokoStreamHudRules() {
    }

    /** What the line's single translation argument is. / 该行唯一的翻译参数是什么。 */
    public enum Argument {
        /** The ability key's display text. / 技能按键的显示文本。 */
        KEY_HINT,
        /** A countdown in whole seconds. / 整秒倒计时。 */
        SECONDS,
        /** A fixed mana amount. / 固定的魔力数值。 */
        MANA,
        /** Her traffic. / 她的流量。 */
        TRAFFIC
    }

    /**
     * A row's translation key, the kind of its argument, that argument's number (unused for the key hint) and colour.
     * 一行的翻译键、参数类别、该参数的数值（按键提示不使用）与颜色。
     */
    public record Line(String key, Argument argument, int value, int color) {
        public boolean keyHint() {
            return argument == Argument.KEY_HINT;
        }
    }

    /**
     * Drawn only on Koko's own client while she is playing and alive (so never as a spectator), in an ACTIVE round
     * once Wathe's round-start fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie has
     * swallowed her (the server refuses a swallowed stream).
     * 仅在可可自己的客户端上、其参与且存活（因此旁观时从不绘制）、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD 显示
     * 且未按 F1 时绘制；被饕餮吞下时从不绘制（服务端拒绝被吞下时的直播）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean koko, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && koko && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return TickTime.ceilSeconds(ticks);
    }

    /**
     * The stream line, in priority order: live (the cooldown already runs behind a stream, so the stream is asked
     * first), cooling down, ready without {@link KokoRules#STREAM_MANA} mana, ready.
     * 直播行，按优先级：直播中（直播期间冷却已在其后计时，因此先判断直播）、冷却中、就绪但魔力不足
     * {@link KokoRules#STREAM_MANA}、就绪。
     */
    public static Line streamLine(int streamTicks, int cooldownTicks, boolean hasManaSystem, int mana) {
        if (streamTicks > 0) {
            return new Line(ACTIVE_KEY, Argument.SECONDS, seconds(streamTicks), READY_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(COOLDOWN_KEY, Argument.SECONDS, seconds(cooldownTicks), COOLDOWN_COLOR);
        }
        if (!hasManaSystem || mana < KokoRules.STREAM_MANA) {
            return new Line(NO_MANA_KEY, Argument.MANA, KokoRules.STREAM_MANA, COOLDOWN_COLOR);
        }
        return new Line(READY_KEY, Argument.KEY_HINT, 0, READY_COLOR);
    }

    /** The traffic row: "traffic N / 1500". / 流量行：“流量 N / 1500”。 */
    public static Line trafficLine(int traffic) {
        return new Line(TRAFFIC_KEY, Argument.TRAFFIC, Math.max(0, traffic), READY_COLOR);
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the stream row, with the shared corner layout (see {@link FiendDashHudRules#rowY}). Both rows are
     * measured together, so a wide row lifts the pair.
     * 直播行的顶部 y，使用共享的角落布局（见 {@link FiendDashHudRules#rowY}）。两行一起测量，因此较宽的一行会让两行一同抬升。
     */
    public static int streamRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                                 boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /** Top y of the traffic row, one row above the stream row. / 流量行的顶部 y，位于直播行上方一行。 */
    public static int trafficRowY(int streamRowY, int fontHeight) {
        return streamRowY - fontHeight - ROW_GAP;
    }

    /**
     * The number a row is measured with: a countdown at {@link FiendDashHudRules#MEASURE_SECONDS} or more and traffic
     * at {@link #MEASURE_TRAFFIC} or more, so the lift decision does not flip as a digit comes or goes.
     * 测量行宽所用的数值：倒计时按不少于 {@link FiendDashHudRules#MEASURE_SECONDS}、流量按不少于 {@link #MEASURE_TRAFFIC}
     * 测量，使抬升判断不会因数字位数变化而来回跳动。
     */
    public static int measuredValue(Line line) {
        return switch (line.argument()) {
            case SECONDS -> FiendDashHudRules.measuredSeconds(line.value());
            case TRAFFIC -> Math.max(line.value(), MEASURE_TRAFFIC);
            default -> line.value();
        };
    }
}
