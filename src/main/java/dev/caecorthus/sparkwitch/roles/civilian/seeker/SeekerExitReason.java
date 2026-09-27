package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import java.util.Locale;

/**
 * Why a remote-view session ended. Only the server decides every reason except the owner's own Shift exit; the
 * owner hears about it through {@code message.sparkwitch.seeker.remote.ended.<name>} (sent by
 * {@code SeekerRemoteSessionService.end}) when {@link #notifiesOwner()}. Device-caused endings (broken, swallowed,
 * recalled, battery) are silent here because {@code SeekerDeviceService} always sends its own device message, which
 * the owner also needs when not viewing; this keeps exactly one message per event.
 * 遥控视角会话结束的原因。除拥有者自己按 Shift 退出外，所有原因都由服务端判定；
 * 当 {@link #notifiesOwner()} 为真时，拥有者会收到 {@code message.sparkwitch.seeker.remote.ended.<name>} 提示
 * （由 {@code SeekerRemoteSessionService.end} 发送）。设备导致的结束（损坏、被吞、回收、电量耗尽）在此静默，
 * 因为 {@code SeekerDeviceService} 总会发送自己的设备消息（拥有者未观看时同样需要），保证每个事件只有一条消息。
 */
public enum SeekerExitReason {
    PLAYER_EXIT(false),
    /** Atomic CAR/CAMERA switch; a new session opens at once. / 原子模式切换，新会话立即打开。 */
    SWITCHED(false),
    CAR_BROKEN(false),
    CAMERA_BROKEN(false),
    CAR_SWALLOWED(false),
    CAR_RECALLED(false),
    BATTERY_DEPLETED(false),
    FOCUS_LOST(true),
    BODY_MOVED(true),
    STUNNED(true),
    KIDNAPPED(true),
    BODY_SWALLOWED(true),
    LAST_STAND(true),
    SPECTATOR(true),
    FEARED(true),
    BLOCKED(true),
    DIED(false),
    ROLE_CHANGED(true),
    CONSOLE_LOST(true),
    OUT_OF_RANGE(true),
    ROUND_END(false),
    DISCONNECTED(false),
    TIMEOUT(true),
    CHEAT_SUSPECT(true);

    private final boolean notifiesOwner;

    SeekerExitReason(boolean notifiesOwner) {
        this.notifiesOwner = notifiesOwner;
    }

    public boolean notifiesOwner() {
        return notifiesOwner;
    }

    public String translationKey() {
        return "message.sparkwitch.seeker.remote.ended." + name().toLowerCase(Locale.ROOT);
    }
}
