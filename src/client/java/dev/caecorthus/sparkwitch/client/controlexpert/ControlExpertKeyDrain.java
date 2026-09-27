package dev.caecorthus.sparkwitch.client.controlexpert;

/**
 * Duck interface implemented on {@code KeyBinding} by the stun key mixin: drops queued presses without firing any
 * {@code wasPressed} return hooks and without touching the held state (toggle sneak/sprint and held keys survive).
 * 由眩晕按键 mixin 在 {@code KeyBinding} 上实现的鸭子接口：丢弃排队的按键次数，不触发任何 {@code wasPressed}
 * 返回钩子，也不改动按住状态（切换潜行/疾跑与按住的键得以保留）。
 */
public interface ControlExpertKeyDrain {
    void sparkwitch$discardQueuedPresses();
}
