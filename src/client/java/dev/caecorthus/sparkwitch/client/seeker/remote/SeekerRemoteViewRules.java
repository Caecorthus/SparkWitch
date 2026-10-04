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
     * The component announced a session but the focus entity never arrived: after
     * {@link SeekerRules#ATTACH_TIMEOUT_TICKS} the client asks the server to close it.
     * 组件已宣告会话但焦点实体一直未到达：等待 {@link SeekerRules#ATTACH_TIMEOUT_TICKS} 刻后，客户端请求服务端关闭。
     */
    public static boolean attachTimedOut(int waitedTicks) {
        return waitedTicks >= SeekerRules.ATTACH_TIMEOUT_TICKS;
    }

    /**
     * Per-tick local exit decision, highest priority first. {@code cameraTaken} means the render camera is no
     * longer our focus: a server camera writer (Taotie, Last Stand, Depression) took over, so it is never restored.
     * 每刻的本地退出判定，按优先级从高到低。{@code cameraTaken} 表示渲染相机已不是我们的焦点：
     * 服务端相机写入方（饕餮、最后一搏、抑郁）接管了相机，因此绝不恢复。
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
        /** The device entity was removed or untracked. / 设备实体被移除或不再追踪。 */
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
