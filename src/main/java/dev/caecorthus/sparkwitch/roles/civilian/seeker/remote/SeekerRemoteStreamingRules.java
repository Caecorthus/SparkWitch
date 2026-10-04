package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Pure rules of the server-side remote streaming ({@link SeekerRemoteStreaming}): chunk-ticket radii and lifetimes,
 * and which device a live session focuses. Ticket radius r puts the ticketed chunk at level {@code 33 - r}: 31 and
 * below tick entities, 32 ticks blocks, 33 is only loaded (entities kept, not ticked).
 * 服务端遥控流式加载（{@link SeekerRemoteStreaming}）的纯规则：区块票据的半径与寿命，以及存活会话的焦点设备。
 * 票据半径 r 使所在区块的等级为 {@code 33 - r}：31 及以下处理实体，32 处理方块，33 仅保持加载（实体保留但不 tick）。
 */
public final class SeekerRemoteStreamingRules {
    /**
     * Cap on the view distance used for the focus ticket: Wathe maps are small, so a wider ring would only load (or
     * generate) terrain far outside the play area. Same cap as SparkStrength's drone view.
     * 焦点票据使用的视距上限：Wathe 地图很小，更大的范围只会加载（甚至生成）游戏区域外的地形。与 SparkStrength 无人机视野相同。
     */
    public static final int MAX_TICKET_VIEW_DISTANCE = 8;
    /** Extra rings so the edge of the sent view is fully loaded first. / 额外的圈数，使发送视野的边缘先完全加载。 */
    public static final int TICKET_MARGIN = 2;
    /** Vanilla's largest valid ticket radius (level 33 - 32 = 1). / 原版允许的最大票据半径。 */
    public static final int MAX_TICKET_RADIUS = 32;
    /**
     * Lifetime of every Seeker ticket after its last refresh. Refreshed each tick while needed, so a position left
     * behind (the car drove on, the session ended, the round was swept) expires on its own with no bookkeeping.
     * 每张搜寻者票据在最后一次刷新后的寿命。需要时每刻刷新，因此被留下的位置（小车开走、会话结束、回合清扫）会自行过期，无需记录。
     */
    public static final int TICKET_EXPIRY_TICKS = 40;
    /**
     * Ticks the owner's tracking is still re-evaluated around the body after a session: the motionless body sends no
     * movement packets, and entities next to it are tracked only once their chunks have been re-sent.
     * 会话结束后继续以本体为准重新评估追踪的刻数：静止的本体不发送移动包，而本体附近的实体要等其区块重新发送后才能被追踪。
     */
    public static final int RESETTLE_TICKS = 40;
    /**
     * Idle car: level 31, so its own chunk ticks entities and the idle tick keeps running far from every player
     * (fall model, below-play-area break, corpse push-off, orphan self-check).
     * 空闲小车：等级 31，所在区块处理实体，使远离所有玩家时空闲 tick 照常运行（下落模型、掉出区域损坏、让开尸体、孤儿自检）。
     */
    public static final int CAR_KEEP_ALIVE_RADIUS = 2;
    /**
     * Idle camera: level 33, loaded but not ticked. A camera is static; its LED and look only change during a session
     * (whose focus ticket ticks it), and its mount can only be broken by someone close enough to tick it anyway.
     * 空闲摄像头：等级 33，保持加载但不 tick。摄像头静止；指示灯与朝向只在会话期间变化（届时焦点票据会让它 tick），
     * 而能破坏其依附面的人本身就足够近，会让它照常 tick。
     */
    public static final int CAMERA_KEEP_ALIVE_RADIUS = 0;

    private SeekerRemoteStreamingRules() {
    }

    /**
     * The engine view distance, exactly as {@code ServerChunkLoadingManager#getViewDistance}: the client's setting
     * clamped to [2, server view distance], with the server value itself clamped to [2, 32].
     * 引擎视距，与 {@code ServerChunkLoadingManager#getViewDistance} 一致：客户端设置截断到 [2, 服务端视距]，服务端视距本身截断到 [2, 32]。
     */
    public static int engineViewDistance(int serverViewDistance, int clientViewDistance) {
        int server = Math.max(2, Math.min(32, serverViewDistance));
        return Math.max(2, Math.min(server, clientViewDistance));
    }

    /** Focus ticket radius: min(view, cap) + margin, within [4, 10]. / 焦点票据半径：min(视距, 上限) + 余量，范围 [4, 10]。 */
    public static int focusTicketRadius(int serverViewDistance, int clientViewDistance) {
        int view = Math.min(engineViewDistance(serverViewDistance, clientViewDistance), MAX_TICKET_VIEW_DISTANCE);
        return Math.min(MAX_TICKET_RADIUS, view + TICKET_MARGIN);
    }

    public static int keepAliveRadius(SeekerDeviceKind kind) {
        return kind == SeekerDeviceKind.CAR ? CAR_KEEP_ALIVE_RADIUS : CAMERA_KEEP_ALIVE_RADIUS;
    }

    /** The device kind a session mode shows, or null without a session. / 会话模式显示的设备种类；无会话时为 null。 */
    @Nullable
    public static SeekerDeviceKind focusKind(SeekerSessionMode mode) {
        return switch (mode) {
            case CAR -> SeekerDeviceKind.CAR;
            case CAMERA -> SeekerDeviceKind.CAMERA;
            case NONE -> null;
        };
    }

    /**
     * The entity id the synced session focuses while the owner still references that device (CAR: the DEPLOYED car
     * with that id; CAMERA: one of the owner's cameras), else -1. A switch or an end changes the synced focus at once,
     * so streaming follows it without waiting for a tick.
     * 同步会话的焦点实体 id，前提是拥有者仍引用该设备（CAR：该 id 的已部署小车；CAMERA：拥有者的摄像头之一），否则为 -1。
     * 切换或结束会立即改变同步焦点，因此流式加载无需等待下一刻即可跟随。
     */
    public static int expectedFocusId(SeekerSessionMode mode, int sessionFocusEntityId, SeekerCarState carState,
                                      int carEntityId, IntPredicate hasCamera) {
        if (sessionFocusEntityId < 0) {
            return -1;
        }
        boolean referenced = switch (mode) {
            case CAR -> carState == SeekerCarState.DEPLOYED && carEntityId == sessionFocusEntityId;
            case CAMERA -> hasCamera.test(sessionFocusEntityId);
            case NONE -> false;
        };
        return referenced ? sessionFocusEntityId : -1;
    }

    public static int expectedFocusId(SeekerState state) {
        return expectedFocusId(state.sessionMode(), state.sessionFocusEntityId(), state.carState(),
                state.carEntityId(), state::hasCamera);
    }
}
