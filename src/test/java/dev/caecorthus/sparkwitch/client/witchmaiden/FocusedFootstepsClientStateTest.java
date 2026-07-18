package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsClientStateTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void confirmsOnlyAPendingTargetWhoseCooldownJumpsFromReadyToTheEffectWindow() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        state.beginRequest(TARGET, 0);

        state.observe(0, true, Set.of(TARGET)::contains);
        assertTrue(state.activeTarget().isEmpty());

        state.acknowledgeOwnerSync(1_800, true, Set.of(TARGET)::contains);
        assertEquals(TARGET, state.activeTarget().orElseThrow());
        assertTrue(state.pendingTarget().isEmpty());
        assertEquals(600, state.remainingEffectTicks());

        state.observe(1_200, true, Set.of(TARGET)::contains);
        assertTrue(state.activeTarget().isEmpty());
        assertEquals(0, state.remainingEffectTicks());
    }

    @Test
    void pendingRequestLocksInputWithoutInventingAFrozenCooldown() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();

        assertTrue(state.beginRequest(TARGET, 0));
        assertTrue(state.isRequestPending());
        assertEquals(0, state.displayCooldownTicks(0));

        state.observe(0, true, Set.of(TARGET)::contains);
        assertEquals(0, state.displayCooldownTicks(0));

        state.resolveUseResult(true, 1_775, true, Set.of(TARGET)::contains);
        assertTrue(state.pendingTarget().isEmpty());
        assertEquals(1_775, state.displayCooldownTicks(1_775));
    }

    @Test
    void explicitResultCountsDownWhenGenericOwnerSyncIsDelayed() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        state.beginRequest(TARGET, 0);
        state.resolveUseResult(true, 1_800, true, Set.of(TARGET)::contains);

        state.observe(0, true, Set.of(TARGET)::contains);

        assertEquals(1_799, state.displayCooldownTicks(0));
        assertEquals(599, state.remainingEffectTicks());
    }

    @Test
    void invalidRequestDoesNotInventAnActiveWindow() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        state.beginRequest(TARGET, 0);
        state.acknowledgeOwnerSync(0, true, Set.of(TARGET)::contains);

        assertTrue(state.activeTarget().isEmpty());
        assertTrue(state.pendingTarget().isEmpty());
        assertEquals(0, state.remainingEffectTicks());
        assertEquals(0, state.displayCooldownTicks(0));
    }

    @Test
    void onePendingRequestCannotBeOverwrittenBeforeTheServerCooldownArrives() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        UUID secondTarget = UUID.fromString("00000000-0000-0000-0000-000000000003");

        assertTrue(state.beginRequest(TARGET, 0));
        assertFalse(state.beginRequest(secondTarget, 0));
        assertEquals(TARGET, state.pendingTarget().orElseThrow());
    }

    @Test
    void rejectedRequestStaysLockedUntilItsOrderedOwnerSyncAcknowledgement() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        UUID retryTarget = UUID.fromString("00000000-0000-0000-0000-000000000003");
        assertTrue(state.beginRequest(TARGET, 0));

        for (int tick = 0; tick < 40; tick++) {
            state.observe(0, true, Set.of(TARGET, retryTarget)::contains);
            assertEquals(TARGET, state.pendingTarget().orElseThrow());
            assertFalse(state.beginRequest(retryTarget, 0));
        }

        state.resolveUseResult(false, 0, true, Set.of(TARGET, retryTarget)::contains);
        assertTrue(state.pendingTarget().isEmpty());
        assertTrue(state.beginRequest(retryTarget, 0));
        assertEquals(retryTarget, state.pendingTarget().orElseThrow());
    }

    @Test
    void delayedSuccessCannotBeMisassociatedWithANewerTarget() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        UUID secondTarget = UUID.fromString("00000000-0000-0000-0000-000000000003");
        assertTrue(state.beginRequest(TARGET, 0));

        for (int tick = 0; tick < 40; tick++) {
            state.observe(0, true, Set.of(TARGET, secondTarget)::contains);
        }
        assertFalse(state.beginRequest(secondTarget, 0));

        state.resolveUseResult(true, 1_800, true, Set.of(TARGET, secondTarget)::contains);
        assertEquals(TARGET, state.activeTarget().orElseThrow());
        assertTrue(state.pendingTarget().isEmpty());
        assertFalse(state.beginRequest(secondTarget, 1_800));
    }

    @Test
    void targetLossRoleLossAndDisconnectClearRoleOwnedState() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        state.beginRequest(TARGET, 0);
        state.acknowledgeOwnerSync(1_800, true, Set.of(TARGET)::contains);
        state.setPage(3);

        state.observe(1_700, true, uuid -> false);
        assertTrue(state.activeTarget().isEmpty());

        state.clearRoundState();
        state.beginRequest(TARGET, 0);
        state.acknowledgeOwnerSync(1_800, false, Set.of(TARGET)::contains);
        assertTrue(state.pendingTarget().isEmpty());
        assertTrue(state.activeTarget().isEmpty());

        state.setPage(3);
        state.clearConnection();
        assertEquals(0, state.page());
        assertTrue(state.pendingTarget().isEmpty());
        assertTrue(state.activeTarget().isEmpty());
    }

    @Test
    void reopeningInventoryKeepsThenClampsThePage() {
        FocusedFootstepsClientState state = new FocusedFootstepsClientState();
        state.setPage(2);

        assertEquals(2, state.clampPage(25));
        assertEquals(0, state.clampPage(1));
        assertEquals(0, state.page());
    }
}
