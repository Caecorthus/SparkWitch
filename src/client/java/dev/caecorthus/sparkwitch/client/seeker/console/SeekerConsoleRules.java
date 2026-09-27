package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
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

    /** Why a device button is disabled; drives the label suffix. / 设备按钮被禁用的原因，决定标签后缀。 */
    public enum Availability {
        /** Enabled. / 可用。 */
        AVAILABLE,
        /** The device is not out (not deployed / not placed); no suffix. / 设备不在场；无后缀。 */
        ABSENT,
        /** Out, but the client cannot resolve it or the player is blocked. / 在场但无法解析或玩家受限。 */
        UNAVAILABLE,
        /** Resolved but beyond the effective radius. / 已解析但超出有效半径。 */
        OUT_OF_RANGE
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

    /**
     * The full intercept gate of §3.10.3. Sneaking is deliberately not an input (toggle-sneak and Niko players must
     * still reach the console).
     * §3.10.3 的完整拦截门槛。刻意不把潜行作为输入（切换式潜行与 Niko 玩家也必须能进入控制台）。
     */
    public static boolean shouldIntercept(boolean confirmedServer, boolean liveSeeker, boolean inSession,
                                          boolean screenOpen, boolean consoleDevice, boolean bypassMatches,
                                          boolean throttleElapsed) {
        return confirmedServer && liveSeeker && !inSession && !screenOpen && consoleDevice && !bypassMatches
                && throttleElapsed;
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
     * "Control car": deployed, resolvable on this client, within the effective radius, and the body not blocked.
     * “操控小车”：已部署、客户端可解析、位于有效半径内，且本体未受限。
     */
    public static Availability carAvailability(SeekerCarState carState, boolean resolvable,
                                               double horizontalDistanceSquared, int radius, boolean blocked) {
        if (carState != SeekerCarState.DEPLOYED) {
            return Availability.ABSENT;
        }
        return deviceAvailability(resolvable, horizontalDistanceSquared, radius, blocked);
    }

    /** "View camera": placed, then the same checks as the car. / “查看摄像头”：已放置，其余与小车相同。 */
    public static Availability cameraAvailability(boolean cameraPlaced, boolean resolvable,
                                                  double horizontalDistanceSquared, int radius, boolean blocked) {
        if (!cameraPlaced) {
            return Availability.ABSENT;
        }
        return deviceAvailability(resolvable, horizontalDistanceSquared, radius, blocked);
    }

    /**
     * Remote recall works from anywhere (no radius, no resolution) while the car is deployed.
     * 远程回收在任何距离都可用（无半径、无需解析），只要求小车已部署。
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
     * Quick connect target: outside a session the car if deployed, otherwise the camera; inside a session the other
     * mode (the server treats it as an atomic switch).
     * 快速连接目标：会话外小车已部署则连小车，否则连摄像头；会话内切换到另一模式（服务端视为原子切换）。
     */
    public static SeekerSessionMode quickConnectTarget(SeekerSessionMode current, SeekerCarState carState) {
        return switch (current) {
            case CAR -> SeekerSessionMode.CAMERA;
            case CAMERA -> SeekerSessionMode.CAR;
            case NONE -> carState == SeekerCarState.DEPLOYED ? SeekerSessionMode.CAR : SeekerSessionMode.CAMERA;
        };
    }

    private static Availability deviceAvailability(boolean resolvable, double horizontalDistanceSquared, int radius,
                                                   boolean blocked) {
        if (blocked || !resolvable) {
            return Availability.UNAVAILABLE;
        }
        return SeekerRules.withinRadius(horizontalDistanceSquared, radius)
                ? Availability.AVAILABLE : Availability.OUT_OF_RANGE;
    }
}
