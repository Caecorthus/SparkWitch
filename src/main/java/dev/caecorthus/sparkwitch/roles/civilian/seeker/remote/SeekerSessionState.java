package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.minecraft.util.math.Vec3d;

/**
 * Server-only, never synced or saved session bookkeeping (anchor, last move seq, move budget, air ticks, reject
 * window, attach deadline), attached to the component as an opaque field. Created by
 * {@code SeekerRemoteSessionService.handleOpen}, cleared by {@code end} and dropped by {@code tick} whenever the synced
 * mode is NONE. Times are absolute server ticks and never leave the server.
 * 仅服务端、从不同步也不存盘的会话记录（锚点、最近移动序号、移动配额、空中刻数、拒绝窗口、挂接截止），作为不透明字段挂在组件上。
 * 由 {@code handleOpen} 创建，由 {@code end} 清除，同步模式为 NONE 时由 {@code tick} 丢弃。时间均为服务端绝对刻，从不离开服务端。
 */
public final class SeekerSessionState {
    final int sessionId;
    final SeekerSessionMode mode;
    final int focusEntityId;
    final Vec3d anchor;
    final long attachDeadline;

    boolean attached;
    long lastMoveTick;
    int lastSeq = -1;
    final SeekerCarMoveRules.Budget budget;
    /**
     * Physics ticks charged to the car's current unsupported stretch; reset only by a move that starts supported (never
     * by a correction, so rejections cannot be farmed to restart the fall model).
     * 小车本段无支撑已计费的物理刻数；只由从有支撑处开始的移动清零（纠正从不清零，因此无法靠刷拒绝来重启下落模型）。
     */
    int airTicks;
    final SeekerCarMoveRules.RejectWindow rejects = new SeekerCarMoveRules.RejectWindow();
    long packetTick = Long.MIN_VALUE;
    int packetsThisTick;
    int ungroundedTicks;

    SeekerSessionState(int sessionId, SeekerSessionMode mode, int focusEntityId, Vec3d anchor, long openedTick,
                       long attachDeadline) {
        this.sessionId = sessionId;
        this.mode = mode;
        this.focusEntityId = focusEntityId;
        this.anchor = anchor;
        this.attachDeadline = attachDeadline;
        this.lastMoveTick = openedTick;
        this.budget = new SeekerCarMoveRules.Budget(openedTick);
    }

    public int sessionId() {
        return sessionId;
    }

    public SeekerSessionMode mode() {
        return mode;
    }

    public int focusEntityId() {
        return focusEntityId;
    }
}
