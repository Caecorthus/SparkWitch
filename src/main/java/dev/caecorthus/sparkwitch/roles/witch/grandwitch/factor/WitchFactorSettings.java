package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

/** Administrator settings are snapshotted at round start. / 管理配置仅在开局时快照。 */
public record WitchFactorSettings(Rule rule, int divisor, int maxCarriers) {
    public enum Rule { Divide, MaxCarrier }
    public static final int UNLIMITED = -1;
    public static final WitchFactorSettings DEFAULT = new WitchFactorSettings(Rule.Divide, 3, 6);

    public WitchFactorSettings {
        if (rule == null || divisor < 1 || maxCarriers < UNLIMITED) {
            throw new IllegalArgumentException("Invalid witch factor settings");
        }
    }

    public int limit(int openingPlayers) {
        return rule == Rule.Divide ? Math.max(0, openingPlayers) / divisor : maxCarriers;
    }

    public static int speedThreshold(int openingPlayers, int limit) {
        int total = limit == UNLIMITED ? Math.max(0, openingPlayers) : Math.max(0, limit);
        return total == 0 ? Integer.MAX_VALUE : total / 2 + total % 2;
    }
}
