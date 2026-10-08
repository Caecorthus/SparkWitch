package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.scope.ScopeVariableZoom;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.util.Util;

/**
 * The local AXMC scope magnification (owner, 2026-10-08): continuous 1x-6x on the shared
 * {@link ScopeVariableZoom}. While scoped the wheel moves the target ({@link UsecRules#ZOOM_NOTCHES_END_TO_END} notches
 * end to end) and the shown value eases toward it; Shift + right-click jumps to the farther end. The scope reopens at
 * the last magnification used: the value outlives scope releases, death and respawn, and is reset to
 * {@link UsecRules#ZOOM_ROUND_START_MAGNIFICATION} (4x) only when a Wathe round begins
 * ({@link UsecInputRules#beginsRound}) and on disconnect, so the first scope-in of every round opens at 4x.
 * Client-only and never sent: the server does not care how far the player zooms. Client thread only.
 * 本地 AXMC 瞄准镜倍率（所有者，2026-10-08）：基于共享 {@link ScopeVariableZoom} 的 1-6 倍连续倍率。开镜时滚轮移动目标
 * （从一端到另一端 {@link UsecRules#ZOOM_NOTCHES_END_TO_END} 格），显示值向其缓动；Shift + 右键跳到较远的一端。瞄准镜以上次
 * 使用的倍率重新打开：该值跨越松开开镜、死亡与重生保留，只在 Wathe 对局开始时（{@link UsecInputRules#beginsRound}）与断线时
 * 重置为 {@link UsecRules#ZOOM_ROUND_START_MAGNIFICATION}（4 倍），因此每局第一次开镜为 4 倍。仅客户端，从不发送：服务端
 * 不关心玩家放大多少。仅客户端线程。
 */
public final class UsecZoomState {
    private static final ScopeVariableZoom ZOOM = new ScopeVariableZoom(UsecRules.ZOOM_MIN_MAGNIFICATION,
            UsecRules.ZOOM_MAX_MAGNIFICATION, UsecRules.ZOOM_NOTCHES_END_TO_END,
            UsecRules.ZOOM_ROUND_START_MAGNIFICATION);
    /** Last observed: no Wathe round in progress (or no world yet). / 上次观察到：没有进行中的对局（或尚无世界）。 */
    private static boolean roundIdle = true;

    private UsecZoomState() {
    }

    /** The shown (eased) magnification now. / 当前显示（缓动后）的倍率。 */
    public static double magnification() {
        return ZOOM.shown(Util.getMeasuringTimeNano());
    }

    /** The magnification the player chose; the shown one eases toward it. / 玩家选定的倍率，显示值向其缓动。 */
    public static double targetMagnification() {
        return ZOOM.target();
    }

    /** FOV multiplier of the shown magnification: 1 at 1x, 0.25 at 4x. / 显示倍率的视野倍率：1 倍为 1，4 倍为 0.25。 */
    public static float fovMultiplier() {
        return ScopeVariableZoom.fovMultiplier(magnification());
    }

    /** A wheel delta while scoped (positive = zoom in). / 开镜时的滚轮增量（正数为放大）。 */
    public static void scroll(double notches) {
        ZOOM.scroll(notches, Util.getMeasuringTimeNano());
    }

    /**
     * Shift + right-click: the raise starts the scope, so the jump lands at once and the scope's own opening ease shows
     * it. / Shift + 右键：举枪时瞄准镜才打开，因此跳转立即到位，由瞄准镜自己的开镜缓动呈现。
     */
    public static void jumpOnRaise() {
        ZOOM.jumpToFartherEnd(Util.getMeasuringTimeNano(), false);
    }

    /** Scoped out: an unfinished ease lands on the target. / 退出开镜：未完成的缓动直接落到目标。 */
    public static void settle() {
        ZOOM.settle();
    }

    /**
     * End of every client tick: a Wathe round that has just begun (or a first sight of one) resets the magnification to
     * 4x. / 每个客户端刻末尾：刚开始的 Wathe 对局（或第一次看到进行中的对局）把倍率重置为 4 倍。
     *
     * @param idle no Wathe round in progress (INACTIVE) or no world / 没有进行中的对局（INACTIVE）或没有世界
     */
    public static void observeRound(boolean idle) {
        if (UsecInputRules.beginsRound(roundIdle, idle)) {
            ZOOM.reset();
        }
        roundIdle = idle;
    }

    /** Disconnect: the next connection starts at 4x. / 断线：下一次连接从 4 倍开始。 */
    public static void reset() {
        ZOOM.reset();
        roundIdle = true;
    }
}
