package dev.caecorthus.sparkwitch.roles.witch.curser;

/** Pure Curser timing constants and authorization predicates. / 诅咒师的纯时长常量与授权判断。 */
public final class CurserRules {
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int COOLDOWN_TICKS = 90 * 20;
    public static final int CONFUSION_TICKS = 10 * 20;
    public static final double RANGE = 8.0;

    private CurserRules() {
    }

    public static boolean canUse(boolean running, boolean activeWraith, boolean promoted, boolean curserRole, int cooldownTicks) {
        return running && activeWraith && promoted && curserRole && cooldownTicks <= 0;
    }
}
