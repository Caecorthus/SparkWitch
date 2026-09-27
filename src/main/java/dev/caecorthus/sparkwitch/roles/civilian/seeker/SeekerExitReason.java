package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import java.util.Locale;

/**
 * Why a remote-view session ended. Only the server decides every reason except the owner's own Shift exit; the
 * owner hears about it through {@code message.sparkwitch.seeker.remote.ended.<name>} when {@link #notifiesOwner()}.
 * 遥控视角会话结束的原因。除拥有者自己按 Shift 退出外，所有原因都由服务端判定；
 * 当 {@link #notifiesOwner()} 为真时，拥有者会收到 {@code message.sparkwitch.seeker.remote.ended.<name>} 提示。
 */
public enum SeekerExitReason {
    PLAYER_EXIT(false),
    /** Atomic CAR/CAMERA switch; a new session opens at once. / 原子模式切换，新会话立即打开。 */
    SWITCHED(false),
    CAR_BROKEN(true),
    CAMERA_BROKEN(true),
    CAR_SWALLOWED(true),
    CAR_RECALLED(true),
    BATTERY_DEPLETED(true),
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
