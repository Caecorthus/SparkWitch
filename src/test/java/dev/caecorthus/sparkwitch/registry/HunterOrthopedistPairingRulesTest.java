package dev.caecorthus.sparkwitch.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterOrthopedistPairingRulesTest {
    @Test
    void randomHunterRequiresEnabledOrthopedistAndAPlayerToPair() {
        assertTrue(HunterOrthopedistPairingRules.canRandomHunterAppear(true, 2));
        assertFalse(HunterOrthopedistPairingRules.canRandomHunterAppear(false, 20));
        assertFalse(HunterOrthopedistPairingRules.canRandomHunterAppear(true, 1));
    }

    @Test
    void hunterNeedsExactlyOneOrthopedistWhileOrthopedistCanExistAlone() {
        assertTrue(HunterOrthopedistPairingRules.needsOrthopedist(true, 0));
        assertFalse(HunterOrthopedistPairingRules.needsOrthopedist(true, 1));
        assertFalse(HunterOrthopedistPairingRules.needsOrthopedist(false, 0));
        assertFalse(HunterOrthopedistPairingRules.needsOrthopedist(false, 1));
    }

    @Test
    void pairingAssignsWhenPossibleAndDemotesHunterWhenEveryOtherSlotIsOccupied() {
        assertEquals(
                HunterOrthopedistPairingRules.PairingAction.ASSIGN_ORTHOPEDIST,
                HunterOrthopedistPairingRules.pairingAction(1, 0, 1, true)
            );
        assertEquals(
                HunterOrthopedistPairingRules.PairingAction.DEMOTE_HUNTERS,
                HunterOrthopedistPairingRules.pairingAction(1, 0, 0, true)
        );
        assertEquals(
                HunterOrthopedistPairingRules.PairingAction.DEMOTE_HUNTERS,
                HunterOrthopedistPairingRules.pairingAction(1, 0, 1, false)
        );
        assertEquals(
                HunterOrthopedistPairingRules.PairingAction.NONE,
                HunterOrthopedistPairingRules.pairingAction(0, 0, 3, true)
        );
        assertEquals(
                HunterOrthopedistPairingRules.PairingAction.NONE,
                HunterOrthopedistPairingRules.pairingAction(1, 1, 0, false)
        );
    }
}
