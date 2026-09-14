package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WitchFactorStateTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID holder = UUID.randomUUID();
    private final UUID killer = UUID.randomUUID();

    @Test
    void openingCapacityUsesFloorAndNeverPopulationDrift() {
        assertEquals(0, WitchFactorState.capacity(-1));
        assertEquals(0, WitchFactorState.capacity(4));
        assertEquals(1, WitchFactorState.capacity(5));
        assertEquals(1, WitchFactorState.capacity(9));
        assertEquals(2, WitchFactorState.capacity(10));
        WitchFactorState state = active(9);
        assertTrue(state.spread(owner, holder));
        assertFalse(state.spread(owner, killer));
        assertEquals(9, state.openingParticipants());
    }

    @Test
    void oneFactorPerPlayerEvenForAnotherOwnerAndNoSelfCast() {
        WitchFactorState state = active(10);
        assertFalse(state.spread(owner, owner));
        assertTrue(state.spread(owner, holder));
        assertFalse(state.spread(owner, holder));
        assertFalse(state.spread(killer, holder));
        assertEquals(1, state.factors().size());
    }

    @Test
    void intervalsHaveNoImmediateRewardAndUseCurrentRoleRates() {
        WitchFactorState.Factor factor = new WitchFactorState.Factor(owner, 0, 0);
        for (int i = 1; i < 600; i++) {
            factor = factor.advance();
            assertNotEquals(0, factor.manaTicks());
            assertNotEquals(0, factor.moodTicks());
        }
        factor = factor.advance();
        assertEquals(0, factor.manaTicks());
        assertEquals(600, factor.moodTicks());
        for (int i = 0; i < 600; i++) {
            factor = factor.advance();
        }
        assertEquals(0, factor.manaTicks());
        assertEquals(0, factor.moodTicks());
        assertEquals(25, WitchFactorState.manaReward(false, false));
        assertEquals(40, WitchFactorState.manaReward(true, false));
        assertEquals(60, WitchFactorState.manaReward(false, true));
        assertEquals(0.1f, WitchFactorState.moodLoss(false));
        assertEquals(0.3f, WitchFactorState.moodLoss(true));
    }

    @Test
    void transferRetainsOwnerAndBothClocksWithoutDuplicateDeathReward() {
        WitchFactorState state = active(5);
        state.spread(owner, holder);
        WitchFactorState.Factor progress = new WitchFactorState.Factor(owner, 599, 899);
        state.factors().put(holder, progress);
        assertTrue(state.afterDeath(holder, killer, true, false));
        assertFalse(state.factors().containsKey(holder));
        assertEquals(progress, state.factors().get(killer));
        assertFalse(state.afterDeath(holder, killer, true, false));
        assertEquals(1, state.factors().size());
        assertEquals(0, state.factors().get(killer).advance().manaTicks());
        assertEquals(900, state.factors().get(killer).advance().moodTicks());
        assertFalse(state.spread(owner, UUID.randomUUID()));
    }

    @Test
    void environmentSelfInvalidOwnerAndAccompliceDeathsRecoverCapacity() {
        for (int scenario = 0; scenario < 5; scenario++) {
            WitchFactorState state = active(5);
            state.spread(owner, holder);
            UUID recipient = switch (scenario) {
                case 0 -> null;
                case 1 -> holder;
                case 2 -> owner;
                default -> killer;
            };
            assertTrue(state.afterDeath(holder, recipient, scenario != 3, scenario == 4));
            assertTrue(state.factors().isEmpty());
            assertTrue(state.spread(owner, UUID.randomUUID()));
        }
    }

    @Test
    void infectedKillerKeepsExistingFactorAndRecoversVictimsFactor() {
        WitchFactorState state = active(10);
        state.spread(owner, holder);
        state.spread(owner, killer);
        WitchFactorState.Factor existing = new WitchFactorState.Factor(owner, 17, 29);
        state.factors().put(killer, existing);
        state.afterDeath(holder, killer, true, false);
        assertEquals(1, state.factors().size());
        assertEquals(existing, state.factors().get(killer));
    }

    @Test
    void privateViewsRevealOnlyOwnFactorsOrAccompliceViewAndRevokeCompletely() {
        WitchFactorState state = active(5);
        UUID anotherOwner = UUID.randomUUID();
        state.spread(owner, holder);
        state.spread(anotherOwner, killer);
        assertEquals(Set.of(holder), state.visibleFor(owner, false, true));
        assertEquals(Set.of(killer), state.visibleFor(anotherOwner, false, true));
        assertEquals(Set.of(holder, killer), state.visibleFor(UUID.randomUUID(), true, true));
        assertTrue(state.visibleFor(owner, false, false).isEmpty());
        assertTrue(state.visibleFor(holder, false, true).isEmpty());
        assertTrue(state.visibleFor(UUID.randomUUID(), true, false).isEmpty());
        state.clear();
        assertTrue(state.visibleFor(owner, true, true).isEmpty());
    }

    @Test
    void recruitmentRecoversHolderAndAnyFactorsOwnedByConvertedGrandWitch() {
        WitchFactorState state = active(10);
        state.spread(owner, holder);
        state.spread(holder, killer);
        state.recoverForRecruitment(holder);
        assertTrue(state.factors().isEmpty());
        state.recoverForRecruitment(holder);
        assertTrue(state.spread(owner, killer));
        assertEquals(new WitchFactorState.Factor(owner, 0, 0), state.factors().get(killer));
    }

    @Test
    void roundResetClearsOwnershipClocksAndOpeningCapacity() {
        WitchFactorState state = active(10);
        state.spread(owner, holder);
        state.begin(4);
        assertTrue(state.factors().isEmpty());
        assertFalse(state.spread(owner, holder));
        state.clear();
        assertFalse(state.active());
        assertEquals(0, state.openingParticipants());
        assertFalse(state.spread(owner, holder));
    }

    private WitchFactorState active(int participants) {
        WitchFactorState state = new WitchFactorState();
        state.begin(participants);
        return state;
    }
}
