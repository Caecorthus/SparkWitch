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
        KILLER,
        WITCH;

        /** Maps persisted names from the original CIVILIAN enum and missing pre-alignment records. */
        public static Alignment fromSerializedName(String name) {
            if (name == null || name.isEmpty()) {
                return KILLER;
            }
            if ("CIVILIAN".equals(name)) {
                return GOOD;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
