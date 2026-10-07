package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;

/**
 * Pure client rules of the Seeker Console (plan §3.10.3–§3.10.5): when the tablet right-click is intercepted, which
 * buttons are enabled, when the console closes itself and what quick connect opens. Every value here is a client-side
 * prediction for presentation only; the server re-validates every packet the console sends.
 * 搜寻者控制台的纯客户端规则（计划 §3.10.3–§3.10.5）：何时拦截平板右键、哪些按钮可用、控制台何时自动关闭，
 * 以及快速连接打开什么。这里的所有值都只是用于展示的客户端预测；控制台发出的每个数据包都由服务端重新校验。
 */
public final class SeekerConsoleRules {
    /** Minimum ticks between two intercepts; SparkStrength's tablet re-fires about every 4 ticks. / 两次拦截的最小间隔刻数。 */
    public static final int INTERCEPT_THROTTLE_TICKS = 10;
    /** Client-side spacing of quick-connect packets, matching the server's open throttle. / 快速连接发包的客户端间隔。 */
    public static final int QUICK_CONNECT_THROTTLE_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;

    /** Car status row variants. / 小车状态行的取值。 */
    public enum CarStatus {
        NONE,
        READY,
        COOLDOWN,
        DEPLOYED,
        SWALLOWED
    }

    /**
     * Why a device button is disabled; drives the label suffix. There is no range state: since 2026-10-04 a device
     * has no distance limit, and a far device is usually not tracked by this client, so entity presence never decides.
     * 设备按钮被禁用的原因，决定标签后缀。不存在距离状态：自 2026-10-04 起设备没有距离限制，且远处的设备通常不被本客户端
     * 追踪，因此从不以实体是否存在作判断。
     */
    public enum Availability {
        /** Enabled. / 可用。 */
        AVAILABLE,
        /** The device is not out (not deployed / not placed); no suffix. / 设备不在场；无后缀。 */
        ABSENT,
        /** Out, but the player is blocked (stunned). / 在场但玩家受限（眩晕）。 */
        UNAVAILABLE
    }

    private SeekerConsoleRules() {
    }

    /**
     * Throttle check; a negative {@code lastTick} means "never", and a clock that went backwards never blocks.
     * 节流判定；{@code lastTick} 为负表示从未拦截，时钟倒退时从不阻止。
     */
    public static boolean throttleElapsed(long nowTick, long lastTick, int intervalTicks) {
        return lastTick < 0 || nowTick < lastTick || nowTick - lastTick >= intervalTicks;
    }

    /** What the tablet intercept does with one use. / 拦截器对一次使用的处理方式。 */
    public enum InterceptAction {
        /** Not ours: vanilla (and SparkStrength) handle the use. / 不归我们处理：交给原版（及 SparkStrength）。 */
        PASS,
        /** Open the console and CONSUME. / 打开控制台并返回 CONSUME。 */
        OPEN,
        /**
         * A console use inside the throttle window: CONSUME without opening, so it never falls through to
         * SparkStrength's own tablet.
         * 节流窗口内的控制台使用：返回 CONSUME 但不打开，避免落到 SparkStrength 自己的平板。
         */
        SWALLOW
    }

    /**
     * The full intercept gate of §3.10.3. Sneaking is deliberately not an input (toggle-sneak and Niko players must
     * still reach the console). The throttle only decides between opening and swallowing: a throttled console use is
     * still consumed, because passing it would send the use packet and SparkStrength would open its own tablet.
     * §3.10.3 的完整拦截门槛。刻意不把潜行作为输入（切换式潜行与 Niko 玩家也必须能进入控制台）。节流只决定打开还是
     * 吞掉：被节流的控制台使用仍返回 CONSUME，否则会发出使用包，由 SparkStrength 打开它自己的平板。
     */
    public static InterceptAction interceptAction(boolean confirmedServer, boolean liveSeeker, boolean inSession,
                                                  boolean screenOpen, boolean consoleDevice, boolean bypassMatches,
                                                  boolean throttleElapsed) {
        if (!confirmedServer || !liveSeeker || inSession || screenOpen || !consoleDevice || bypassMatches) {
            return InterceptAction.PASS;
        }
        return throttleElapsed ? InterceptAction.OPEN : InterceptAction.SWALLOW;
    }

    public static CarStatus carStatus(SeekerCarState carState, int cooldownTicks) {
        return switch (carState) {
            case NONE -> CarStatus.NONE;
            case READY -> cooldownTicks > 0 ? CarStatus.COOLDOWN : CarStatus.READY;
            case DEPLOYED -> CarStatus.DEPLOYED;
            case SWALLOWED -> CarStatus.SWALLOWED;
        };
    }

    /** Rounds remaining ticks up to whole seconds (never shows 0 while cooling down). / 剩余刻数向上取整为秒。 */
    public static int secondsCeil(int ticks) {
        return ticks <= 0 ? 0 : (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** The battery row is shown only while a car is out. / 仅当小车在场时显示电量行。 */
    public static boolean batteryVisible(SeekerCarState carState) {
        return carState == SeekerCarState.DEPLOYED;
    }

    /**
     * "Control car": deployed (owner-synced state, at any distance) and the body not blocked.
     * “操控小车”：已部署（拥有者同步状态，距离不限）且本体未受限。
     */
    public static Availability carAvailability(SeekerCarState carState, boolean blocked) {
        if (carState != SeekerCarState.DEPLOYED) {
            return Availability.ABSENT;
        }
        return blocked ? Availability.UNAVAILABLE : Availability.AVAILABLE;
    }

    /**
     * "View camera": at least one camera in the owner-synced list (at any distance) and the body not blocked; the
     * server picks which camera.
     * “查看摄像头”：拥有者同步列表中至少有一台摄像头（距离不限）且本体未受限；具体哪台由服务端选择。
     */
    public static Availability cameraAvailability(int cameraCount, boolean blocked) {
        if (cameraCount <= 0) {
            return Availability.ABSENT;
        }
        return blocked ? Availability.UNAVAILABLE : Availability.AVAILABLE;
    }

    /**
     * Remote recall works from anywhere (no entity resolution) while the car is deployed.
     * 远程回收在任何距离都可用（无需解析实体），只要求小车已部署。
     */
    public static Availability recallAvailability(SeekerCarState carState, boolean blocked) {
        if (carState != SeekerCarState.DEPLOYED) {
            return Availability.ABSENT;
        }
        return blocked ? Availability.UNAVAILABLE : Availability.AVAILABLE;
    }

    /** "Police network" exists only for the SparkStrength tablet held in the used hand. / 仅手持 SS 平板时显示“警察网络”。 */
    public static boolean networkVisible(boolean heldIsTablet) {
        return heldIsTablet;
    }

    /** SparkStrength opens its tablet only from the hotbar, so warn for the offhand. / SS 只认快捷栏，副手时提示。 */
    public static boolean hotbarHintVisible(boolean heldIsTablet, boolean offhand) {
        return heldIsTablet && offhand;
    }

    /**
     * The console closes itself when the player stops being a live Seeker, loses every console device, or a remote
     * session starts.
     * 当玩家不再是存活的搜寻者、失去所有控制台设备，或遥控会话开始时，控制台自动关闭。
     */
    public static boolean shouldAutoClose(boolean liveSeeker, boolean hasConsoleDevice, boolean inSession) {
        return !liveSeeker || !hasConsoleDevice || inSession;
    }

    /**
     * Quick connect target. Inside a session it toggles to the other mode (the server treats it as an atomic switch;
     * a CAMERA request is the server's default camera). Outside a session it opens the car when the car is deployed
     * and either usable or no camera is usable (so the refusal names the car), otherwise the default camera.
     * {@code carUsable}/{@code cameraUsable} are client predictions; the server re-validates.
     * 快速连接目标。会话内切换到另一模式（服务端视为原子切换；摄像头请求由服务端选择默认摄像头）。会话外：小车已部署且
     * 可用、或没有任何可用摄像头时连小车（拒绝提示会针对小车），否则连默认摄像头。{@code carUsable}/{@code cameraUsable}
     * 只是客户端预测，服务端会重新校验。
     */
    public static SeekerSessionMode quickConnectTarget(SeekerSessionMode current, SeekerCarState carState,
                                                       boolean carUsable, boolean cameraUsable) {
        return switch (current) {
            case CAR -> SeekerSessionMode.CAMERA;
            case CAMERA -> SeekerSessionMode.CAR;
            case NONE -> carState == SeekerCarState.DEPLOYED && (carUsable || !cameraUsable)
                    ? SeekerSessionMode.CAR : SeekerSessionMode.CAMERA;
        };
    }
}
