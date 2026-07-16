package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlackRavenPerceptionStateTest {
    private static final UUID MATCH_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MATCH_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final BlackRavenIdentitySnapshot SNAPSHOT = new BlackRavenIdentitySnapshot(
            TARGET,
            "Alex",
            "wathe:detective",
            0x55AAFF
    );

    @Test
    void qualifyingTicksPauseAndResumeIncludingFractionalProgress() {
        BlackRavenPerceptionState state = new BlackRavenPerceptionState();
        state.bindMatch(MATCH_ONE);

        state.accumulate(TARGET, 19, () -> SNAPSHOT);
        assertEquals(0, state.points(TARGET));
        assertEquals(19, state.fractionalTicks(TARGET));

        state.accumulate(TARGET, 0, () -> SNAPSHOT);
        state.accumulate(TARGET, 1, () -> SNAPSHOT);
        assertEquals(1, state.points(TARGET));
        assertEquals(0, state.fractionalTicks(TARGET));
    }

    @Test
    void revealIsCappedAndFreezesFirstAuthoritativeSnapshot() {
        BlackRavenPerceptionState state = new BlackRavenPerceptionState();
        state.bindMatch(MATCH_ONE);

        assertTrue(state.accumulate(TARGET, 200, () -> SNAPSHOT));
        assertEquals(10, state.points(TARGET));
        assertEquals(SNAPSHOT, state.snapshot(TARGET));
        assertFalse(state.accumulate(TARGET, 400, () -> new BlackRavenIdentitySnapshot(
                TARGET,
                "Changed",
                "wathe:killer",
                0xFF0000
        )));
        assertEquals(SNAPSHOT, state.snapshot(TARGET));
    }

    @Test
    void matchChangeClearsProgressAndCompletedSnapshots() {
        BlackRavenPerceptionState state = new BlackRavenPerceptionState();
        state.bindMatch(MATCH_ONE);
        state.accumulate(TARGET, 200, () -> SNAPSHOT);

        assertTrue(state.bindMatch(MATCH_TWO));
        assertEquals(0, state.points(TARGET));
        assertNull(state.snapshot(TARGET));
    }

    @Test
    void ownerProjectionContainsOnlyCompletedSnapshotsInInsertionOrder() {
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000004");
        BlackRavenIdentitySnapshot secondSnapshot = new BlackRavenIdentitySnapshot(
                second,
                "Steve",
                "wathe:civilian",
                0xFFFFFF
        );
        BlackRavenPerceptionState state = new BlackRavenPerceptionState();
        state.bindMatch(MATCH_ONE);
        state.accumulate(TARGET, 199, () -> SNAPSHOT);
        state.accumulate(second, 200, () -> secondSnapshot);
        state.accumulate(TARGET, 1, () -> SNAPSHOT);

        assertEquals(List.of(secondSnapshot, SNAPSHOT), state.completedSnapshots());
    }
}
