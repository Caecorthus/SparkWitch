package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet.RiftGateConsoleRules;

import java.util.List;

/**
 * Pure client rules of the Rift Gate console (plan §15): when the tablet use is intercepted, what an incoming snapshot
 * does, when the open console polls or closes itself, and the distance/direction read-out. Every value is a client-side
 * prediction for presentation only; the server re-validates every packet.
 * 裂隙门控制台的纯客户端规则（plan §15）：何时拦截平板使用、收到的快照做什么、打开中的控制台何时轮询或自行关闭，
 * 以及距离与方向的显示。这里的值都只是用于展示的客户端预测；每个数据包都由服务端重新校验。
 */
public final class RiftGateConsoleClientRules {
    /** Minimum ticks between two intercepts; held use re-fires about every 4 ticks. / 两次拦截的最小间隔刻数。 */
    public static final int INTERCEPT_THROTTLE_TICKS = 10;
    /** An unanswered open request stops counting after this long. / 未获应答的打开请求在此时长后失效。 */
    public static final int PENDING_OPEN_TIMEOUT_TICKS = 60;
    /** Poll cadence of an open console (about once per second). / 打开中的控制台的轮询间隔（约每秒一次）。 */
    public static final int POLL_INTERVAL_TICKS = RiftGateConsoleRules.POLL_INTERVAL_TICKS;
    /**
     * A console that hears nothing for longer than the server's session timeout plus two polls closes itself (the
     * server ignores polls of sessions it no longer knows).
     * 超过服务端会话超时再加两次轮询仍未收到任何快照的控制台会自行关闭（服务端忽略它不再认识的会话的轮询）。
     */
    public static final int STALE_TIMEOUT_TICKS = RiftGateConsoleRules.SESSION_TIMEOUT_TICKS + 2 * POLL_INTERVAL_TICKS;
    /** The row's 「确认关闭？」 window (about 3 s). / 行内「确认关闭？」的窗口（约 3 秒）。 */
    public static final int CONFIRM_WINDOW_TICKS = 60;
    /**
     * A second click sooner than this after arming is ignored, so a double-click never closes a gate by accident.
     * 待确认后过早的第二次点击会被忽略，避免双击误关门。
     */
    public static final int CONFIRM_MIN_DELAY_TICKS = 5;
    /** A confirmed row stays greyed until the snapshot drops it or this passes. / 已确认的行保持灰色，直到快照移除或超时。 */
    public static final int CLOSING_TIMEOUT_TICKS = 40;
    /** Eight-way arrows, clockwise from "ahead"; plain BMP glyphs (emoji render as boxes). / 八方向箭头，从“正前方”顺时针。 */
    static final List<String> ARROWS = List.of("↑", "↗", "→", "↘", "↓", "↙", "←", "↖");
    /** Shown instead of an arrow when the gate is (horizontally) right here. / 门就在脚下（水平方向）时代替箭头显示。 */
    static final String HERE = "·";
    private static final double HERE_DISTANCE = 0.75;

    private RiftGateConsoleClientRules() {
    }

    /** What the tablet intercept does with one use. / 拦截器对一次使用的处理方式。 */
    public enum InterceptAction {
        /** Not ours: vanilla (and SparkStrength) handle the use. / 不归我们处理：交给原版（及 SparkStrength）。 */
        PASS,
        /** Ask the server to open the console and CONSUME. / 请求服务端打开控制台并返回 CONSUME。 */
        OPEN,
        /**
         * A console use inside the throttle window or while an open request is outstanding: CONSUME without sending, so
         * it never falls through to SparkStrength's own tablet.
         * 节流窗口内或已有未应答打开请求时的控制台使用：返回 CONSUME 且不发包，避免落到 SparkStrength 自己的平板。
         */
        SWALLOW
    }

    /**
     * The intercept gate (copy of the Seeker console's shape, Riftwalker-owned): a SparkWitch server that accepts the
     * console request, a live Riftwalker by RAW role, not inside a gate, no screen open, the tablet used from the
     * hotbar (main hand), and no armed bypass. Sneaking is deliberately not an input.
     * 拦截门槛（仿照搜寻者控制台的形状，隙行者自有）：可接收控制台请求的 SparkWitch 服务器、原始职业为隙行者且存活、
     * 不在门内、没有打开的界面、从快捷栏（主手）使用平板，且没有待用的旁路。刻意不把潜行作为输入。
     */
    public static InterceptAction interceptAction(boolean serverReady, boolean liveRiftwalker, boolean insideGate,
                                                  boolean screenOpen, boolean hotbarTabletUse, boolean bypassMatches,
                                                  boolean throttleElapsed, boolean openPending) {
        if (!serverReady || !liveRiftwalker || insideGate || screenOpen || !hotbarTabletUse || bypassMatches) {
            return InterceptAction.PASS;
        }
        return throttleElapsed && !openPending ? InterceptAction.OPEN : InterceptAction.SWALLOW;
    }

    /** What one {@code rift_gate_console} snapshot does on the client. / 一份快照在客户端上的作用。 */
    public enum SnapshotAction {
        /** Open the console for this new session. / 为这个新会话打开控制台。 */
        OPEN,
        /** Update the open console of the same session. / 刷新同一会话的已打开控制台。 */
        REFRESH,
        /** CLOSED while the console is open. / 控制台打开时收到 CLOSED。 */
        CLOSE,
        /** The outstanding open request was answered without opening. / 未完成的打开请求已获应答但不打开。 */
        DROP_PENDING,
        /** Stale or unsolicited: nothing. / 过期或未请求的快照：不处理。 */
        IGNORE
    }

    /**
     * Snapshot dispatch. The server only answers requests, so a console opens only for an outstanding open request and
     * only for a session newer than every session seen ({@code sessionFloor}), never on top of another screen; an open
     * console only accepts its own session; CLOSED closes the console or answers the open request.
     * 快照分派。服务端只应答请求，因此控制台只会为未完成的打开请求、且只为比已见过的所有会话都新的会话
     * （{@code sessionFloor}）打开，绝不盖在其他界面之上；已打开的控制台只接受自己的会话；CLOSED 关闭控制台或应答打开请求。
     */
    public static SnapshotAction snapshotAction(int snapshotSession, boolean consoleOpen, int consoleSession,
                                                boolean openPending, boolean otherScreenOpen, int sessionFloor) {
        if (snapshotSession == RiftGateConsoleS2CPacket.CLOSED) {
            if (consoleOpen) {
                return SnapshotAction.CLOSE;
            }
            return openPending ? SnapshotAction.DROP_PENDING : SnapshotAction.IGNORE;
        }
        if (consoleOpen) {
            return snapshotSession == consoleSession ? SnapshotAction.REFRESH : SnapshotAction.IGNORE;
        }
        if (!openPending || snapshotSession <= sessionFloor) {
            return SnapshotAction.IGNORE;
        }
        return otherScreenOpen ? SnapshotAction.DROP_PENDING : SnapshotAction.OPEN;
    }

    /**
     * The console closes itself when the server stops being ready, the player stops being a live Riftwalker, enters a
     * gate, loses the hotbar tablet, or no snapshot arrived for {@link #STALE_TIMEOUT_TICKS}.
     * 当服务器不再就绪、玩家不再是存活隙行者、进入门内、快捷栏失去平板，或 {@link #STALE_TIMEOUT_TICKS} 内没有收到快照时，
     * 控制台自行关闭。
     */
    public static boolean shouldAutoClose(boolean serverReady, boolean liveRiftwalker, boolean insideGate,
                                          boolean tabletInHotbar, long ticksSinceSnapshot) {
        return !serverReady || !liveRiftwalker || insideGate || !tabletInHotbar
                || ticksSinceSnapshot > STALE_TIMEOUT_TICKS;
    }

    /**
     * Throttle check; a negative {@code lastTick} means "never" and a clock that went backwards never blocks.
     * 节流判定；{@code lastTick} 为负表示从未发生，时钟倒退时从不阻止。
     */
    public static boolean throttleElapsed(long nowTick, long lastTick, int intervalTicks) {
        return lastTick < 0 || nowTick < lastTick || nowTick - lastTick >= intervalTicks;
    }

    /** An open request still counts as outstanding. / 打开请求仍视为未完成。 */
    public static boolean openPending(long nowTick, long requestTick) {
        return requestTick >= 0 && nowTick >= requestTick && nowTick - requestTick < PENDING_OPEN_TIMEOUT_TICKS;
    }

    /** Whole blocks of the 3-D distance. / 三维距离的整格数。 */
    public static long blocks(double dx, double dy, double dz) {
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * Index into {@link #ARROWS} of the gate's bearing relative to the player's yaw (0 = ahead, 2 = right, 4 = behind,
     * 6 = left), or -1 when the gate is horizontally right here. Minecraft yaw: 0 faces +z, increasing yaw turns right.
     * 门相对玩家朝向的方位在 {@link #ARROWS} 中的下标（0 正前、2 右、4 后、6 左）；门在水平方向就在脚下时为 -1。
     * Minecraft 偏航：0 朝向 +z，偏航增大即向右转。
     */
    public static int arrowIndex(float playerYaw, double dx, double dz) {
        if (dx * dx + dz * dz < HERE_DISTANCE * HERE_DISTANCE) {
            return -1;
        }
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = wrapDegrees(bearing - playerYaw);
        return Math.floorMod((int) Math.round(relative / 45.0), ARROWS.size());
    }

    /** The glyph for {@link #arrowIndex}. / {@link #arrowIndex} 对应的字形。 */
    public static String arrow(float playerYaw, double dx, double dz) {
        int index = arrowIndex(playerYaw, dx, dz);
        return index < 0 ? HERE : ARROWS.get(index);
    }

    /** Clamps a list scroll offset. / 限制列表滚动偏移。 */
    public static int clampScroll(int scroll, int rows, int visibleRows) {
        return Math.max(0, Math.min(scroll, Math.max(0, rows - Math.max(1, visibleRows))));
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
