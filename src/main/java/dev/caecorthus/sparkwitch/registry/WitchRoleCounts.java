package dev.caecorthus.sparkwitch.registry;

/**
 * Pure role count rules for SparkWitch.
 * SparkWitch 职业数量公式集中在这里，避免分配器和测试各写一份。
 */
public final class WitchRoleCounts {
    public static final int WITCH_THRESHOLD = 18;
    public static final int APPRENTICE_WITCH_THRESHOLD = 24;
    public static final int BEWITCHED_INTERVAL = 6;
    public static final int APPRENTICE_DIVIDEND = 8;

    private WitchRoleCounts() {
    }

    public static Counts forPlayerCount(int totalPlayers) {
        return new Counts(
                grandWitches(totalPlayers),
                bewitched(totalPlayers),
                apprenticeWitches(totalPlayers)
        );
    }

    public static int grandWitches(int totalPlayers) {
        return totalPlayers >= WITCH_THRESHOLD ? 1 : 0;
    }

    /**
     * Bewitched dealt in a Grand Witch round (D1, the former recruit quota): fewer than 18 players give none, 18-23
     * give 1, then +1 per further 6 (24-29 → 2, 30-35 → 3, ...).
     * 有大魔女的对局发放的魔化使数量（D1，沿用原招募名额）：不足 18 人为 0，18-23 人为 1，此后每多 6 人加 1
     * （24-29 人为 2，30-35 人为 3，依此类推）。
     */
    public static int bewitched(int totalPlayers) {
        return totalPlayers < WITCH_THRESHOLD ? 0 : (totalPlayers - WITCH_THRESHOLD) / BEWITCHED_INTERVAL + 1;
    }

    public static int apprenticeWitches(int totalPlayers) {
        if (totalPlayers < APPRENTICE_WITCH_THRESHOLD) {
            return 0;
        }
        return Math.floorDiv(totalPlayers, APPRENTICE_DIVIDEND);
    }

    public record Counts(int grandWitches, int bewitched, int apprenticeWitches) {
    }
}
