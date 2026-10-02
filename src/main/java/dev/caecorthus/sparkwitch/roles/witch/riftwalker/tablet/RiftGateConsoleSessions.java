package dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure per-player console session table (server thread only; no Minecraft types, unit tested). One console per player:
 * {@link #open} issues a fresh, strictly increasing, non-zero id and supersedes the previous one. The client polls the
 * open console every {@link RiftGateConsoleRules#POLL_INTERVAL_TICKS}; a session that is not polled for
 * {@link RiftGateConsoleRules#SESSION_TIMEOUT_TICKS} is dead, which is how the server learns the console closed (there is
 * no close packet). Only the current, live session id may close gates.
 * 纯粹的每玩家控制台会话表（仅服务端线程；不含 Minecraft 类型，可单元测试）。每名玩家只有一个控制台：{@link #open}
 * 发放新的、严格递增的非零 id，并取代上一个会话。客户端每 {@link RiftGateConsoleRules#POLL_INTERVAL_TICKS} 轮询一次；
 * 超过 {@link RiftGateConsoleRules#SESSION_TIMEOUT_TICKS} 未轮询的会话视为失效——服务端以此得知控制台已关闭（没有关闭包）。
 * 只有当前且有效的会话 id 才能关门。
 */
public final class RiftGateConsoleSessions {
    /** What to do with one poll. / 对一次轮询的处理。 */
    public enum PollResult {
        /** Live current session and the snapshot throttle elapsed: validate, then answer. / 有效的当前会话且节流已过：校验后应答。 */
        SNAPSHOT,
        /** Live current session inside the snapshot throttle: kept alive, no answer. / 节流窗口内：保持存活，不应答。 */
        THROTTLED,
        /** The current session expired: tell the client to close. / 当前会话已失效：通知客户端关闭。 */
        CLOSE,
        /**
         * A superseded, ended or unknown id: answer nothing, so a late reply can never close a newer console (the client
         * closes a console that stops receiving snapshots on its own).
         * 已被取代、已结束或未知的 id：不应答，迟到的回复绝不会关掉更新的控制台（收不到快照的控制台由客户端自行关闭）。
         */
        IGNORE
    }

    /** Session/throttle part of a close request. / 关门请求的会话与节流判定。 */
    public enum CloseCheck {
        ALLOWED,
        /**
         * The player has no live session (it expired, ended or never existed): the console that sent this is dead, so
         * tell it to close.
         * 玩家没有有效会话（已过期、已结束或从未存在）：发送该请求的控制台已失效，应通知其关闭。
         */
        STALE,
        /**
         * Another live session is current: an old console's late request; ignore it without CLOSED so the newer console
         * stays open.
         * 当前另有有效会话：旧控制台迟到的请求；忽略且不回复 CLOSED，让更新的控制台保持打开。
         */
        SUPERSEDED,
        THROTTLED
    }

    /** "No session". / “无会话”。 */
    public static final int NONE = 0;

    private final Map<UUID, State> states = new HashMap<>();
    private int nextId = 1;

    /**
     * Opens a new console session and returns its id, or {@link #NONE} inside the open throttle.
     * 打开新的控制台会话并返回其 id；处于打开节流窗口内时返回 {@link #NONE}。
     */
    public int open(UUID player, long now) {
        State state = states.computeIfAbsent(player, ignored -> new State());
        if (!elapsed(now, state.lastOpenTick, RiftGateConsoleRules.OPEN_THROTTLE_TICKS)) {
            return NONE;
        }
        state.sessionId = allocate();
        state.lastOpenTick = now;
        state.lastPollTick = now;
        state.lastSnapshotTick = now;
        return state.sessionId;
    }

    public PollResult poll(UUID player, int sessionId, long now) {
        State state = states.get(player);
        if (state == null || state.sessionId == NONE || sessionId != state.sessionId) {
            return PollResult.IGNORE;
        }
        if (expired(state, now)) {
            state.sessionId = NONE;
            return PollResult.CLOSE;
        }
        state.lastPollTick = now;
        if (!elapsed(now, state.lastSnapshotTick, RiftGateConsoleRules.SNAPSHOT_THROTTLE_TICKS)) {
            return PollResult.THROTTLED;
        }
        state.lastSnapshotTick = now;
        return PollResult.SNAPSHOT;
    }

    /**
     * Session and throttle check of a close request; an allowed close also counts as console activity.
     * 关门请求的会话与节流判定；被允许的关门同样算作控制台活动。
     */
    public CloseCheck checkClose(UUID player, int sessionId, long now) {
        State state = states.get(player);
        if (state != null && state.sessionId != NONE && expired(state, now)) {
            // The current session died: end it so its late polls are ignored rather than answered again.
            // 当前会话已失效：结束它，使其迟到的轮询被忽略而不是再次应答。
            state.sessionId = NONE;
        }
        if (state == null || state.sessionId == NONE) {
            return CloseCheck.STALE;
        }
        if (sessionId != state.sessionId) {
            return CloseCheck.SUPERSEDED;
        }
        if (!elapsed(now, state.lastCloseTick, RiftGateConsoleRules.CLOSE_THROTTLE_TICKS)) {
            return CloseCheck.THROTTLED;
        }
        state.lastCloseTick = now;
        state.lastPollTick = now;
        return CloseCheck.ALLOWED;
    }

    /** The current live session id, or {@link #NONE}. / 当前有效会话 id，或 {@link #NONE}。 */
    public int current(UUID player, long now) {
        State state = states.get(player);
        return state == null || state.sessionId == NONE || expired(state, now) ? NONE : state.sessionId;
    }

    /** Ends the session but keeps the throttles (denied or invalidated). / 结束会话但保留节流（被拒绝或失效）。 */
    public void end(UUID player) {
        State state = states.get(player);
        if (state != null) {
            state.sessionId = NONE;
        }
    }

    /** Forgets the player (disconnect). / 遗忘该玩家（断线）。 */
    public void remove(UUID player) {
        states.remove(player);
    }

    /** Forgets everyone (server stop). / 遗忘所有人（服务器停止）。 */
    public void clear() {
        states.clear();
    }

    private int allocate() {
        int id = nextId;
        // Strictly increasing so the client can tell a fresh console from a superseded one; never 0 (CLOSED/OPEN).
        // 严格递增，便于客户端区分新控制台与被取代的旧控制台；绝不为 0（CLOSED/OPEN）。
        nextId = id == Integer.MAX_VALUE ? 1 : id + 1;
        return id;
    }

    private static boolean expired(State state, long now) {
        return now - state.lastPollTick > RiftGateConsoleRules.SESSION_TIMEOUT_TICKS;
    }

    /**
     * Throttle check; a negative {@code last} means "never" and a clock that went backwards never blocks.
     * 节流判定；{@code last} 为负表示从未发生，时钟倒退时从不阻止。
     */
    static boolean elapsed(long now, long last, int interval) {
        return last < 0 || now < last || now - last >= interval;
    }

    private static final class State {
        private int sessionId = NONE;
        private long lastOpenTick = -1L;
        private long lastPollTick = -1L;
        private long lastSnapshotTick = -1L;
        private long lastCloseTick = -1L;
    }
}
