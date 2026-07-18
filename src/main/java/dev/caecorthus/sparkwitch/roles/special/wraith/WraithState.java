package dev.caecorthus.sparkwitch.roles.special.wraith;

/**
 * Stable Wraith alignment captured before the original role is replaced.
 * 在原身份被替换前保存的稳定冤魂阵营值。
 */
public final class WraithState {
    private WraithState() {
    }

    public enum Alignment {
        CIVILIAN,
        KILLER,
        WITCH;

        public static Alignment fromSerializedName(String name) {
            if (name == null) {
                return null;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
