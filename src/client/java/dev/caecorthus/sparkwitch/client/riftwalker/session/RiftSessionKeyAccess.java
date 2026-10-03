package dev.caecorthus.sparkwitch.client.riftwalker.session;

/**
 * Duck interface implemented on {@code KeyBinding} by {@code RiftSessionKeyBindingMixin}: raw access to the queued
 * press count and the held flag, bypassing every {@code isPressed}/{@code wasPressed} hook (ours included), so the
 * in-gate controller reads Shift, A/D, use and keys 1/2 while the rest of the game sees them released. It never loops
 * {@code wasPressed()} (that would feed other mods' return hooks) and never unpresses a key.
 * 由 {@code RiftSessionKeyBindingMixin} 在 {@code KeyBinding} 上实现的鸭子接口：直接读取积压的按下次数与按住标记，绕过所有
 * {@code isPressed}/{@code wasPressed} 钩子（包括我们自己的），使门内控制器能读取 Shift、A/D、使用键与 1/2 键，而游戏其余部分
 * 视其为未按下。从不循环调用 {@code wasPressed()}（会触发其他模组的返回钩子），也从不强制松开按键。
 */
public interface RiftSessionKeyAccess {
    /** Zeroes the queued press count. / 清零积压的按下次数。 */
    void sparkwitch$riftDrainPresses();

    /** Returns and zeroes the queued press count. / 返回并清零积压的按下次数。 */
    int sparkwitch$riftTakePresses();

    /** The raw held flag (toggle keys report their toggled state). / 原始按住标记（切换式按键为其切换状态）。 */
    boolean sparkwitch$riftRawPressed();
}
