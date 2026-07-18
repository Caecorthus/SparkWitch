package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PoisonApplePlateStateTest {
    private static final UUID PLACER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MATCH = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_MATCH = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void firstSuccessfulTakeIsSafeAndKeepsTheTrapArmed() {
        PoisonApplePlateState.TakeResult result = PoisonApplePlateState.armed(PLACER, MATCH)
                .onSuccessfulTake(MATCH);

        assertFalse(result.shouldPoison());
        assertNull(result.poisonerUuid());
        assertEquals(1, result.nextState().successfulTakeCount());
        assertEquals(PLACER, result.nextState().placerUuid());
        assertEquals(MATCH, result.nextState().matchUuid());
    }

    @Test
    void secondSuccessfulTakePoisonsWithTheOriginalPlacerAndDisarms() {
        PoisonApplePlateState state = PoisonApplePlateState.armed(PLACER, MATCH)
                .onSuccessfulTake(MATCH)
                .nextState();

        PoisonApplePlateState.TakeResult result = state.onSuccessfulTake(MATCH);

        assertTrue(result.shouldPoison());
        assertEquals(PLACER, result.poisonerUuid());
        assertNull(result.nextState());
    }

    @Test
    void staleMatchStateClearsWithoutPoisoning() {
        PoisonApplePlateState.TakeResult result = PoisonApplePlateState.armed(PLACER, MATCH)
                .onSuccessfulTake(OTHER_MATCH);

        assertFalse(result.shouldPoison());
        assertNull(result.poisonerUuid());
        assertNull(result.nextState());
    }

    @Test
    void loadedStateBelongsOnlyToItsOriginalMatch() {
        PoisonApplePlateState state = PoisonApplePlateState.armed(PLACER, MATCH);

        assertTrue(state.belongsTo(MATCH));
        assertFalse(state.belongsTo(OTHER_MATCH));
        assertFalse(state.belongsTo(null));
    }
}
