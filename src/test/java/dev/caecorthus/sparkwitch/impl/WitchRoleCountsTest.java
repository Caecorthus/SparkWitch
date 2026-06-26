package dev.caecorthus.sparkwitch.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitchRoleCountsTest {
    @Test
    void keepsWitchesDisabledBelowTwentyFourPlayers() {
        WitchRoleCounts.Counts counts = WitchRoleCounts.forPlayerCount(23);

        assertEquals(0, counts.grandWitches());
        assertEquals(0, counts.accomplices());
        assertEquals(0, counts.apprenticeWitches());
    }

    @Test
    void matchesAcceptedThresholdExamples() {
        assertCounts(24, 1, 0, 3);
        assertCounts(28, 1, 1, 3);
        assertCounts(32, 1, 2, 4);
    }

    private static void assertCounts(int players, int grandWitches, int accomplices, int apprenticeWitches) {
        WitchRoleCounts.Counts counts = WitchRoleCounts.forPlayerCount(players);

        assertEquals(grandWitches, counts.grandWitches());
        assertEquals(accomplices, counts.accomplices());
        assertEquals(apprenticeWitches, counts.apprenticeWitches());
    }
}
