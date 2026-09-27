package dev.caecorthus.sparkwitch.client.seeker.remote;

/**
 * Duck interface implemented on KeyBinding by the remote key mixin: drains queued presses.
 * TODO(WP-10a): implement through SeekerRemoteKeyBindingMixin. / 待 WP-10a 通过 SeekerRemoteKeyBindingMixin 实现。
 * 由遥控按键 mixin 在 KeyBinding 上实现的鸭子接口：清空积压的按键。
 */
public interface SeekerRemoteKeyDrain {
    void sparkwitch$drainPresses();
}
