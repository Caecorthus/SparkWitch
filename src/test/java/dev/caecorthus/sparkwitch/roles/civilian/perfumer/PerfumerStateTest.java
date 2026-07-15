package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfumerStateTest {
    @Test
    void keepsMultipleMarksPrivateToTheirOwningState() {
        PerfumerState firstOwner = new PerfumerState();
        PerfumerState secondOwner = new PerfumerState();
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();

        assertTrue(firstOwner.mark(firstTarget));
        assertTrue(firstOwner.mark(secondTarget));
        assertFalse(firstOwner.mark(firstTarget));
        assertTrue(firstOwner.isMarked(firstTarget));
        assertTrue(firstOwner.isMarked(secondTarget));
        assertFalse(secondOwner.isMarked(firstTarget));
    }

    @Test
    void onlyACompletedPostMarkKillPromotesTheTargetToBloody() {
        PerfumerState state = new PerfumerState();
        UUID markedTarget = UUID.randomUUID();
        UUID unmarkedTarget = UUID.randomUUID();
        state.mark(markedTarget);

        assertFalse(state.promoteToBloody(unmarkedTarget));
        assertTrue(state.promoteToBloody(markedTarget));
        assertTrue(state.isBloody(markedTarget));
        assertFalse(state.isMarked(markedTarget));
        assertFalse(state.mark(markedTarget));
    }

    @Test
    void deathRemovesBothMarkedAndBloodyTargetState() {
        PerfumerState state = new PerfumerState();
        UUID markedTarget = UUID.randomUUID();
        UUID bloodyTarget = UUID.randomUUID();
        state.mark(markedTarget);
        state.mark(bloodyTarget);
        state.promoteToBloody(bloodyTarget);

        assertTrue(state.removeTarget(markedTarget));
        assertTrue(state.removeTarget(bloodyTarget));
        assertFalse(state.isMarked(markedTarget));
        assertFalse(state.isBloody(bloodyTarget));
        assertFalse(state.removeTarget(UUID.randomUUID()));
    }

    @Test
    void colognePulsesTenTimesAndRefreshRestartsOneTimer() {
        PerfumerState state = new PerfumerState();
        state.startCologne();

        int pulses = tickAndCountPulses(state, 50);
        assertEquals(2, pulses);
        assertEquals(150, state.cologneTicks());

        state.startCologne();
        assertEquals(PerfumerRules.COLOGNE_DURATION_TICKS, state.cologneTicks());
        assertEquals(10, tickAndCountPulses(state, PerfumerRules.COLOGNE_DURATION_TICKS));
        assertEquals(0, state.cologneTicks());
        assertFalse(state.tickCologne());
    }

    @Test
    void stoppingCologneDoesNotEraseTheOwnersRoundTracking() {
        PerfumerState state = new PerfumerState();
        UUID target = UUID.randomUUID();
        state.mark(target);
        state.promoteToBloody(target);
        state.startCologne();

        state.stopCologne();

        assertTrue(state.isBloody(target));
        assertEquals(0, state.cologneTicks());
        assertFalse(state.tickCologne());
    }

    @Test
    void clearDropsAllRoundAndHealingState() {
        PerfumerState state = new PerfumerState();
        UUID target = UUID.randomUUID();
        state.mark(target);
        state.promoteToBloody(target);
        state.startCologne();

        state.clear();

        assertFalse(state.isBloody(target));
        assertEquals(0, state.cologneTicks());
        assertFalse(state.tickCologne());
    }

    private static int tickAndCountPulses(PerfumerState state, int ticks) {
        int pulses = 0;
        for (int index = 0; index < ticks; index++) {
            if (state.tickCologne()) {
                pulses++;
            }
        }
        return pulses;
    }
}
