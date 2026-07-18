package dev.caecorthus.sparkwitch.client.witchmaiden;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsInventoryRulesTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALIVE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEAD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID UNASSIGNED = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void ownerGateRequiresConfirmedRunningLivingExactRoleWithFocusedSkill() {
        assertTrue(FocusedFootstepsInventoryRules.ownerEligible(true, true, true, true, true));
        assertFalse(FocusedFootstepsInventoryRules.ownerEligible(false, true, true, true, true));
        assertFalse(FocusedFootstepsInventoryRules.ownerEligible(true, false, true, true, true));
        assertFalse(FocusedFootstepsInventoryRules.ownerEligible(true, true, false, true, true));
        assertFalse(FocusedFootstepsInventoryRules.ownerEligible(true, true, true, false, true));
        assertFalse(FocusedFootstepsInventoryRules.ownerEligible(true, true, true, true, false));
    }

    @Test
    void liveRosterPreservesOnlineOrderAndFiltersSelfDeadAndUnassignedPlayers() {
        List<UUID> result = FocusedFootstepsInventoryRules.candidates(
                OWNER,
                List.of(OWNER, UNASSIGNED, ALIVE, DEAD),
                Set.of(OWNER, ALIVE, DEAD)::contains,
                Set.of(DEAD)::contains
        );

        assertEquals(List.of(ALIVE), result);
    }
}
