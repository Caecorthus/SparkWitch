package dev.caecorthus.sparkwitch.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitchRoleCountsTest {
    @Test
    void keepsWitchesDisabledBelowEighteenPlayers() {
        WitchRoleCounts.Counts counts = WitchRoleCounts.forPlayerCount(17);

        assertEquals(0, counts.grandWitches());
        assertEquals(0, counts.accomplices());
        assertEquals(0, counts.apprenticeWitches());
    }

    @Test
    void matchesAcceptedThresholdExamples() {
        assertCounts(18, 1, 0, 0);
        assertCounts(23, 1, 0, 0);
        assertCounts(24, 1, 1, 3);
        assertCounts(30, 1, 2, 3);
    }

    private static void assertCounts(int players, int grandWitches, int accomplices, int apprenticeWitches) {
        WitchRoleCounts.Counts counts = WitchRoleCounts.forPlayerCount(players);

        assertEquals(grandWitches, counts.grandWitches());
        assertEquals(accomplices, counts.accomplices());
        assertEquals(apprenticeWitches, counts.apprenticeWitches());
    }
}
