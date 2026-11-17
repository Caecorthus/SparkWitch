package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;

/**
 * Pure state machine of 《Fade》 on one client. A window is identified by its synced absolute end tick, so a resync in
 * the middle of a window (a crawler released, a role change) never restarts the clip, and each new window plays it
 * exactly once. The clip is exactly as long as the window (10 s), so it starts at full volume with no fade-in and its
 * start offset does not matter; when the window ends before the clip (the caster died or was reset, the round
 * stopped) or the clip outlives the window by a few ticks of latency, it fades out over
 * {@link SphinxHaoRules#FADE_OUT_TICKS}. No Minecraft client types, so it is unit-testable.
 * 单个客户端上《Fade》的纯状态机。窗口以同步的绝对结束刻标识，因此窗口中途的重新同步（陆泳者被释放、职业变更）不会重播，每个新
 * 窗口恰好播放一次。片段与窗口等长（10 秒），因此以满音量开始、不淡入，起始偏移无关紧要；窗口先于片段结束时（施法者死亡或被重置、
 * 回合停止），或片段因延迟比窗口多出几刻时，在 {@link SphinxHaoRules#FADE_OUT_TICKS} 内淡出。不含 Minecraft 客户端类型，可做单元
 * 测试。
 */
public final class SphinxHaoFadeBgmRules {
    /** "No window played yet" (end ticks are positive world ticks). / “尚未播放任何窗口”（结束刻为正的世界刻）。 */
    public static final long NO_WINDOW = Long.MIN_VALUE;

    private SphinxHaoFadeBgmRules() {
    }

    /**
     * The active window this client sees, or {@link #NO_WINDOW}: a running round, a confirmed server and an open
     * window. Every client counts (alive, dead or spectating: owner Q2, everyone hears it).
     * 本客户端看到的进行中窗口，或 {@link #NO_WINDOW}：回合进行中、服务器已确认且窗口开启。每个客户端都算（存活、死亡或旁观：
     * 所有者 Q2，所有人都听得到）。
     */
    public static long activeWindow(boolean confirmedServer, boolean roundRunning, boolean roarActive, long endTick) {
        return confirmedServer && roundRunning && roarActive ? endTick : NO_WINDOW;
    }

    /**
     * One client tick of the starter: whether the clip starts now (an open window this client has not played yet),
     * and the window remembered as played afterwards (the open one, or none once no window runs).
     * 启动器的一个客户端刻：此刻是否开始播放（本端尚未播放过的开启窗口），以及之后记为已播放的窗口（开启的那个；没有窗口时为无）。
     */
    public static Step step(long activeWindow, long playedWindow) {
        return new Step(activeWindow != NO_WINDOW && activeWindow != playedWindow, activeWindow);
    }

    /** Result of {@link #step}. / {@link #step} 的结果。 */
    public record Step(boolean start, long playedWindow) {
    }

    /**
     * The clip's fade counter after one tick: full ({@link SphinxHaoRules#FADE_OUT_TICKS}) while its own window runs,
     * one less per tick once it does not. Below zero the clip is done.
     * 片段一刻后的淡出计数：自身窗口进行中时为满（{@link SphinxHaoRules#FADE_OUT_TICKS}），窗口不在时每刻减一。小于零即播放结束。
     */
    public static int nextFade(boolean ownWindowActive, int fade) {
        return ownWindowActive ? SphinxHaoRules.FADE_OUT_TICKS : fade - 1;
    }

    /** Whether the clip is done (faded out). / 片段是否已结束（已淡出）。 */
    public static boolean done(int fade) {
        return fade < 0;
    }

    /** Volume for a fade counter, clamped to [0, 1]. / 淡出计数对应的音量，限制在 [0, 1]。 */
    public static float volume(int fade) {
        return Math.max(0.0F, Math.min(1.0F, fade / (float) SphinxHaoRules.FADE_OUT_TICKS));
    }
}
