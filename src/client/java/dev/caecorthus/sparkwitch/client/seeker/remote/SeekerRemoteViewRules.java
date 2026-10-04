package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraLookRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure client rules of the remote view (WP-10a internal): the key allowlist, look clamps for the car and the camera
 * cone, the owner-client play-area clamp for the car, move-send cadence, camera-cycle press edges and spacing, and the
 * per-tick local exit decision. Nothing here touches the client instance, so every rule is unit-tested without a game.
 * 遥控视角的纯客户端规则（WP-10a 内部）：按键白名单、小车与摄像头锥角的视角钳制、小车在拥有者客户端的游戏区域钳制、
 * 移动包发送节奏、摄像头切换的按下沿与间隔，以及每刻的本地退出判定。这里不触碰客户端实例，因此每条规则都可以脱离游戏做单元测试。
 */
public final class SeekerRemoteViewRules {
    /** Simple Voice Chat's key category (push-to-talk, whisper, mute). / Simple Voice Chat 的按键分类。 */
    public static final String VOICE_CHAT_CATEGORY = "key.categories.voicechat";
    /**
     * SparkWitch's configurable skill key 2 (N), used by the Seeker's quick connect / mode switch (WP-11).
     * SparkWitch 可配置的技能键 2（N），供搜寻者快速连接与模式切换使用（WP-11）。
     */
    public static final String SECONDARY_SKILL_KEY = "key.sparkwitch.secondary_skill";
    /**
     * Keys that keep working while viewing: movement (drives the car), sneak (exit), player list, screenshot,
     * fullscreen and skill key 2. Everything else, including jump, sprint, hotbar, inventory, drop, use and attack,
     * reads as released.
     * 观看期间仍可用的按键：移动（驾驶小车）、潜行（退出）、玩家列表、截图、全屏与技能键 2。
     * 其余按键（包括跳跃、疾跑、快捷栏、背包、丢弃、使用与攻击）都视为未按下。
     */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "key.forward",
            "key.back",
            "key.left",
            "key.right",
            "key.sneak",
            "key.playerlist",
            "key.screenshot",
            "key.fullscreen",
            SECONDARY_SKILL_KEY);
    /** Vanilla {@code Entity#changeLookDirection} degrees per scaled mouse unit. / 原版鼠标视角换算系数。 */
    public static final float LOOK_SCALE = 0.15F;
    /**
     * A parked car still sends its position this often, so the server's move timeout
     * ({@link SeekerRules#MOVE_TIMEOUT_TICKS}) never ends a session that is only looking around.
     * 停着的小车也按此间隔发送位置，避免只转视角的会话被服务端的移动超时结束。
     */
    public static final int MOVE_KEEPALIVE_TICKS = 20;
    /**
     * Client spacing of camera-cycle requests: the server's open throttle ({@link SeekerRules#OPEN_THROTTLE_TICKS},
     * shared by the console, quick connect and cycling) plus a two-tick margin for tick jitter, counted from the last
     * cycle request or the last session start or switch, whichever is later.
     * 摄像头切换请求的客户端间隔：服务端打开节流（{@link SeekerRules#OPEN_THROTTLE_TICKS}，由控制台、快速连接与切换共用）
     * 加上两刻的抖动余量，从上次切换请求或上次会话开始/切换（取较晚者）算起。
     */
    public static final int CAMERA_CYCLE_THROTTLE_TICKS = SeekerRules.OPEN_THROTTLE_TICKS + 2;
    /**
     * Fail-safe cap on the post-view body hold; the body's chunks normally return within a few ticks of the server
     * ending the session. / 观看结束后本体保持的兜底上限；服务端结束会话后本体区块通常几刻内就会送达。
     */
    public static final int BODY_SETTLE_MAX_TICKS = 200;
    private static final double MOVE_EPSILON = 1.0E-4;
    private static final float YAW_EPSILON = 0.01F;
    private static final double BOUNDS_EPSILON = 1.0E-6;

    private SeekerRemoteViewRules() {
    }

    /** True when the key keeps its normal state while viewing. / 观看期间该按键保持正常状态时返回 true。 */
    public static boolean isAllowedKey(@Nullable String translationKey, @Nullable String category) {
        return VOICE_CHAT_CATEGORY.equals(category) || (translationKey != null && ALLOWED_KEYS.contains(translationKey));
    }

    /** Raw input axis from two opposing keys (vanilla Input convention). / 由一对相反按键得到的原始输入轴。 */
    public static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }

    /**
     * A camera-cycle press counts only on a fresh key-down: the key read released on the previous tick
     * ({@code wasDown} false) and is down now or has a press queued since (a tap shorter than a tick). OS key repeat
     * queues presses only while the key stays down, so a held key cycles once. The caller treats the key as down when
     * a viewpoint is bound, so a key held through a session start or switch must be released before it cycles.
     * 摄像头切换只认新的按下沿：该键上一刻读取为松开（{@code wasDown} 为 false），且现在按下或其间积压了按下（短于一刻的轻点）。
     * 系统按键重复只会在按住期间积压按下次数，因此按住只切换一次。调用方在绑定视点时把按键视为按下，因此在会话开始或切换时
     * 一直按住的键必须先松开才能再切换。
     */
    public static boolean freshPress(boolean wasDown, boolean down, boolean queued) {
        return !wasDown && (down || queued);
    }

    /** Car view pitch: view-only, within +-60 degrees. / 小车视角俯仰：只影响画面，限制在 ±60°。 */
    public static float clampCarPitch(float pitch) {
        return MathHelper.clamp(pitch, -SeekerRules.CAR_PITCH_LIMIT, SeekerRules.CAR_PITCH_LIMIT);
    }

    // The camera cone delegates to the common SeekerCameraLookRules, which the server also clamps look packets with.
    // 摄像头锥角委托给两端通用的 SeekerCameraLookRules，服务端钳制视角包时使用同一套规则。

    /**
     * Horizontal centre of the camera cone: the wall normal, or the mount yaw for floor and ceiling cameras.
     * 摄像头锥角的水平中心：墙面取法线方向，地面与天花板取安装朝向。
     */
    public static float coneYawCenter(Direction facing, float mountYaw) {
        return SeekerCameraLookRules.coneYawCenter(facing, mountYaw);
    }

    /**
     * Vertical centre of the camera cone: level on walls, looking up from the floor, down from the ceiling.
     * 摄像头锥角的垂直中心：墙面水平，地面朝上看，天花板朝下看。
     */
    public static float conePitchCenter(Direction facing) {
        return SeekerCameraLookRules.conePitchCenter(facing);
    }

    /**
     * Clamps a yaw into centre +-70 degrees while keeping its winding, so interpolation never spins.
     * 将 yaw 钳制到中心 ±70° 内，并保留其圈数，插值时不会整圈旋转。
     */
    public static float clampConeYaw(float yaw, float center) {
        return SeekerCameraLookRules.clampConeYaw(yaw, center);
    }

    /** Pitch within centre +-45 degrees and the vanilla +-90 limit. / pitch 限制在中心 ±45° 与原版 ±90° 之内。 */
    public static float clampConePitch(float pitch, float center) {
        return SeekerCameraLookRules.clampConePitch(pitch, center);
    }

    /**
     * Owner-client soft wall: pulls a car target back inside the play area (minus half the traversal width); a null
     * area skips the bound. There is no distance limit from the body (2026-10-04). The server still clamps and
     * corrects; this only keeps snap-backs rare.
     * 拥有者客户端的软墙：把小车目标拉回游戏区域（扣除半个通行箱宽度）之内；区域为空时跳过该约束。
     * 与本体之间没有距离限制（2026-10-04）。服务端仍会钳制并纠正，这里只是减少回拉。
     */
    public static Vec3d clampHorizontalTarget(@Nullable Box playArea, Vec3d target) {
        if (playArea == null) {
            return target;
        }
        return new Vec3d(clampInto(target.x, playArea.minX, playArea.maxX), target.y,
                clampInto(target.z, playArea.minZ, playArea.maxZ));
    }

    /**
     * How far a position lies outside the play-area bounds (0 when inside or when the area is unknown).
     * 位置超出游戏区域约束的程度（在区域内或区域未知时为 0）。
     */
    public static double boundsExcess(@Nullable Box playArea, Vec3d position) {
        if (playArea == null) {
            return 0.0;
        }
        return overflow(position.x, playArea.minX, playArea.maxX) + overflow(position.z, playArea.minZ, playArea.maxZ);
    }

    /**
     * A local move is kept when it ends inside the bounds or at least never ends further outside than it started.
     * 本地移动在终点位于范围内、或至少不比起点更靠外时才保留。
     */
    public static boolean acceptsMove(@Nullable Box playArea, Vec3d from, Vec3d to) {
        return boundsExcess(playArea, to) <= boundsExcess(playArea, from) + BOUNDS_EPSILON;
    }

    /** Position or heading changed since the last sent move. / 自上次发送后位置或朝向有变化。 */
    public static boolean moved(@Nullable Vec3d lastSent, float lastSentYaw, Vec3d position, float yaw) {
        return lastSent == null
                || lastSent.squaredDistanceTo(position) > MOVE_EPSILON * MOVE_EPSILON
                || Math.abs(MathHelper.wrapDegrees(yaw - lastSentYaw)) > YAW_EPSILON;
    }

    public static boolean keepaliveDue(int ticksSinceSend) {
        return ticksSinceSend >= MOVE_KEEPALIVE_TICKS;
    }

    /**
     * Connecting grace: the session is held (body locked, CONNECTING panel) while the focus entity is not on this
     * client; after {@link SeekerRules#ATTACH_TIMEOUT_TICKS} waiting ticks the client gives up and asks the server to
     * close. The same constant is the server's CAR attach deadline, so the client never gives up first.
     * 连接宽限：焦点实体尚未到达本客户端时保持会话（本体锁定、显示“正在连接”面板）；等待
     * {@link SeekerRules#ATTACH_TIMEOUT_TICKS} 刻后客户端放弃并请求服务端关闭。该常量同时是服务端 CAR 挂接截止，
     * 因此客户端不会先于服务端放弃。
     */
    public static boolean attachTimedOut(int waitedTicks) {
        return waitedTicks >= SeekerRules.ATTACH_TIMEOUT_TICKS;
    }

    /**
     * One tick of the link to the session focus (unlimited range, 2026-10-04: a far device streams in late, and a
     * re-tracked device comes back as a new instance with the same id). {@code waitedTicks} already counts this tick.
     * 与会话焦点的单刻连接判定（无限距离，2026-10-04：远处设备会晚到，重新追踪的设备会以同 id 的新实例出现）。
     * {@code waitedTicks} 已包含本刻。
     */
    public static LinkStep linkStep(boolean resolved, boolean sameInstance, int waitedTicks) {
        if (resolved) {
            return sameInstance ? LinkStep.KEEP : LinkStep.BIND;
        }
        return attachTimedOut(waitedTicks) ? LinkStep.GIVE_UP : LinkStep.WAIT;
    }

    /**
     * After a bind, the CONNECTING mask stays until the 3x3 chunks around the focus are on this client (the server
     * force-tracks the device before its terrain arrives, which then streams in over several ticks), or until the
     * grace ran out, so the mask is never permanent; Shift still leaves at any time.
     * 绑定之后，“正在连接”遮罩保持到焦点周围 3x3 区块到达本客户端（服务端会在地形到达之前强制追踪设备，地形随后在数刻内推送到达），
     * 或宽限耗尽为止，因此遮罩绝不会永久存在；Shift 随时可以退出。
     */
    public static boolean revealsView(boolean focusTerrainLoaded, int ticksSinceBind) {
        return focusTerrainLoaded || attachTimedOut(ticksSinceBind);
    }

    /**
     * Whether the local body's own (SELF) moves are cancelled. While viewing, only when the chunks under its hitbox
     * are missing on this client (the server streams the device's chunks instead, and 1.21.1's
     * {@code ClientWorld.isChunkLoaded} is always true, so vanilla would let it fall through the empty chunk and report
     * that). After the view, while settling, until the 3x3 chunks around it are back. Spectators are never held.
     * 本体自身（SELF）移动是否取消。观看期间仅当其碰撞箱下方区块在本客户端缺失时（服务端改为推送设备周围的区块，而 1.21.1 的
     * {@code ClientWorld.isChunkLoaded} 恒为 true，原版会让本体穿过空区块下落并上报）；观看结束后的稳定期内保持到周围 3x3
     * 区块重新送达。旁观者从不保持。
     */
    public static boolean holdsBody(boolean viewing, boolean settling, boolean spectator, boolean ownChunksLoaded,
                                    boolean surroundingsLoaded) {
        if (spectator) {
            return false;
        }
        if (viewing) {
            return !ownChunksLoaded;
        }
        return settling && !surroundingsLoaded;
    }

    /**
     * The post-view settle window ends when the body's surroundings are back, the local body changed (respawn,
     * reconnect), or the cap ran out. / 观看结束后的稳定期在本体周围区块送达、本地本体更换（重生、重连）或达到上限时结束。
     */
    public static boolean settleEnds(boolean sameBody, int remainingTicks, boolean surroundingsLoaded) {
        return !sameBody || remainingTicks <= 0 || surroundingsLoaded;
    }

    /**
     * Per-tick local exit decision, highest priority first. {@code cameraTaken} means the render camera is no
     * longer our focus: a server camera writer (Taotie, Last Stand, Depression) took over, so it is never restored.
     * {@code focusGone} means the connecting grace ran out ({@link #linkStep} gave up).
     * 每刻的本地退出判定，按优先级从高到低。{@code cameraTaken} 表示渲染相机已不是我们的焦点：
     * 服务端相机写入方（饕餮、最后一搏、抑郁）接管了相机，因此绝不恢复。{@code focusGone} 表示连接宽限已耗尽
     * （{@link #linkStep} 放弃）。
     */
    public static LocalExit localExit(boolean playerChanged, SeekerSessionMode serverMode, boolean sessionChanged,
                                      boolean cameraTaken, boolean focusGone, boolean sneakPressed) {
        if (playerChanged) {
            return LocalExit.PLAYER_CHANGED;
        }
        if (serverMode == SeekerSessionMode.NONE) {
            return LocalExit.SERVER_ENDED;
        }
        if (cameraTaken) {
            return LocalExit.CAMERA_TAKEN;
        }
        if (sessionChanged) {
            return LocalExit.SWITCHED;
        }
        if (focusGone) {
            return LocalExit.FOCUS_LOST;
        }
        return sneakPressed ? LocalExit.PLAYER_EXIT : LocalExit.NONE;
    }

    private static double clampInto(double value, double min, double max) {
        double half = SeekerRules.CAR_TRAVERSAL_WIDTH / 2.0;
        double low = min + half;
        double high = max - half;
        return low > high ? (min + max) / 2.0 : MathHelper.clamp(value, low, high);
    }

    private static double overflow(double value, double min, double max) {
        double half = SeekerRules.CAR_TRAVERSAL_WIDTH / 2.0;
        double low = min + half;
        double high = max - half;
        if (low > high) {
            return Math.abs(value - (min + max) / 2.0);
        }
        return Math.max(0.0, low - value) + Math.max(0.0, value - high);
    }

    /**
     * Result of {@link #linkStep}: KEEP the bound focus, BIND a newly resolved focus in place (session start, atomic
     * switch, or a re-tracked instance), WAIT while connecting (the last view stays, the body stays locked), or GIVE_UP
     * after the grace (local exit {@link LocalExit#FOCUS_LOST}).
     * {@link #linkStep} 的结果：KEEP 保持已绑定焦点；BIND 原地绑定新解析的焦点（会话开始、原子切换或重新追踪的新实例）；
     * WAIT 连接中（保留上一画面，本体保持锁定）；GIVE_UP 宽限耗尽（本地退出 {@link LocalExit#FOCUS_LOST}）。
     */
    public enum LinkStep {
        KEEP,
        BIND,
        WAIT,
        GIVE_UP
    }

    /**
     * Local reaction to one tick of state. {@link #SWITCHED} retargets in place when the new focus resolves.
     * 对单刻状态的本地反应。{@link #SWITCHED} 在新焦点可解析时原地切换。
     */
    public enum LocalExit {
        NONE(false, false),
        /** The local player object changed (respawn, join). / 本地玩家对象已更换（重生、加入）。 */
        PLAYER_CHANGED(true, false),
        /** The server synced mode NONE. / 服务端同步了 NONE 模式。 */
        SERVER_ENDED(true, false),
        /** A server camera writer owns the camera now. / 服务端相机写入方已接管相机。 */
        CAMERA_TAKEN(false, true),
        /**
         * Atomic switch (new session id): car to camera, camera to car, or one camera to another.
         * 原子切换（新的会话 id）：小车切到摄像头、摄像头切到小车，或从一台摄像头切到另一台。
         */
        SWITCHED(true, false),
        /**
         * The focus did not (re)appear on this client within the connecting grace.
         * 焦点未能在连接宽限内（重新）出现在本客户端。
         */
        FOCUS_LOST(true, true),
        /** Sneak pressed: predicted exit. / 按下潜行：预测退出。 */
        PLAYER_EXIT(true, true);

        private final boolean restoresCamera;
        private final boolean sendsClose;

        LocalExit(boolean restoresCamera, boolean sendsClose) {
            this.restoresCamera = restoresCamera;
            this.sendsClose = sendsClose;
        }

        /** Whether the end edge may hand the camera back (still only if it is still ours). / 结束沿是否可以归还相机。 */
        public boolean restoresCamera() {
            return restoresCamera;
        }

        /** Whether the client tells the server with {@code seeker_remote_close}. / 是否发送关闭包通知服务端。 */
        public boolean sendsClose() {
            return sendsClose;
        }
    }
}
