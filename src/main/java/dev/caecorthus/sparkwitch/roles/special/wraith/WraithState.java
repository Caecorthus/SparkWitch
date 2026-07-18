package dev.caecorthus.sparkwitch.roles.special.wraith;

/**
 * Pure Wraith state values shared by lifecycle storage and promotion routing.
 * 冤魂生命周期存储与晋升路由共享的纯状态值。
 */
public final class WraithState {
    private WraithState() {
    }

    public enum Alignment {
        GOOD,
        KILLER
    }
}
