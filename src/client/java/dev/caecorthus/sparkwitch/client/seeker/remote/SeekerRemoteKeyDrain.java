package dev.caecorthus.sparkwitch.client.seeker.remote;

/**
 * Duck interface implemented on {@code KeyBinding} by {@code SeekerRemoteKeyBindingMixin}: zeroes the queued press
 * count directly. It never loops {@code wasPressed()} (that would feed other mods' return hooks) and never
 * unpresses the key (that would drop a held key's state).
 * 由 {@code SeekerRemoteKeyBindingMixin} 在 {@code KeyBinding} 上实现的鸭子接口：直接清零积压的按键次数。
 * 从不循环调用 {@code wasPressed()}（会触发其他模组的返回钩子），也从不强制松开按键（会丢失按住状态）。
 */
public interface SeekerRemoteKeyDrain {
    void sparkwitch$drainPresses();
}
