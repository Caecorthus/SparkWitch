package dev.caecorthus.sparkwitch.registry;

/**
 * Pure role count rules for SparkWitch.
 * SparkWitch 职业数量公式集中在这里，避免分配器和测试各写一份。
 */
public final class WitchRoleCounts {
    public static final int WITCH_THRESHOLD = 18;
    public static final int APPRENTICE_WITCH_THRESHOLD = 24;
    public static final int ACCOMPLICE_INTERVAL = 6;
    public static final int APPRENTICE_DIVIDEND = 8;

    private WitchRoleCounts() {
    }

    public static Counts forPlayerCount(int totalPlayers) {
        return new Counts(
                grandWitches(totalPlayers),
                accomplices(totalPlayers),
                apprenticeWitches(totalPlayers)
        );
    }

    public static int grandWitches(int totalPlayers) {
        return totalPlayers >= WITCH_THRESHOLD ? 1 : 0;
    }

    public static int accomplices(int totalPlayers) {
        if (totalPlayers < WITCH_THRESHOLD) {
            return 0;
        }
        return Math.floorDiv(totalPlayers - WITCH_THRESHOLD, ACCOMPLICE_INTERVAL);
    }

    public static int apprenticeWitches(int totalPlayers) {
        if (totalPlayers < APPRENTICE_WITCH_THRESHOLD) {
            return 0;
        }
        return Math.floorDiv(totalPlayers, APPRENTICE_DIVIDEND);
    }

    public record Counts(int grandWitches, int accomplices, int apprenticeWitches) {
    }
}
