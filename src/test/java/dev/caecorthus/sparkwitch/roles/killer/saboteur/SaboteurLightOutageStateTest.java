package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurLightOutageStateTest {
    private static final String LAMP = "lamp";

    @Test
    void localOnlyRestoresTheCapturedLitAndActiveState() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();

        state.beginLocal(LAMP, 400L, true, true);

        assertTrue(state.hasLocal(LAMP));
        assertTrue(state.expireLocals(399L).isEmpty());
        assertEquals(List.of(new SaboteurLightOutageState.Restore<>(LAMP, true, true)),
                state.expireLocals(400L));
        assertTrue(state.isEmpty());
    }

    @Test
    void originalOffLampPreservesItsActiveValue() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();

        state.beginLocal(LAMP, 20L, false, true);

        assertEquals(List.of(new SaboteurLightOutageState.Restore<>(LAMP, false, true)),
                state.expireLocals(20L));
    }

    @Test
    void overlappingLocalActivationsExpireIndependently() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        state.beginLocal(LAMP, 400L, true, true);
        state.beginLocal(LAMP, 500L, false, false);

        assertTrue(state.expireLocals(400L).isEmpty());
        assertTrue(state.hasLocal(LAMP));
        assertEquals(List.of(new SaboteurLightOutageState.Restore<>(LAMP, true, true)),
                state.expireLocals(500L));
    }

    @Test
    void localBeforeGlobalRestoresThePreLocalSnapshotAfterWatheEndsLast() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginLocal(LAMP, 400L, true, true);
        state.beginWathe(LAMP, wathe, false, false);

        assertTrue(state.expireLocals(400L).isEmpty());
        assertEquals(SaboteurLightOutageState.WatheEndDecision.RESTORE_AFTER_NATIVE,
                state.endWathe(LAMP, wathe));
        assertEquals(new SaboteurLightOutageState.Restore<>(LAMP, true, true),
                state.finishWatheEnd(LAMP));
        assertTrue(state.isEmpty());
    }

    @Test
    void globalBeforeLocalStaysDarkWhenGlobalEndsFirst() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginWathe(LAMP, wathe, true, true);
        state.beginLocal(LAMP, 400L, false, false);

        assertEquals(SaboteurLightOutageState.WatheEndDecision.KEEP_DARK,
                state.endWathe(LAMP, wathe));
        assertTrue(state.hasLocal(LAMP));
        assertEquals(List.of(new SaboteurLightOutageState.Restore<>(LAMP, true, true)),
                state.expireLocals(400L));
    }

    @Test
    void globalBeforeLocalRestoresTheGlobalSnapshotWhenLocalExpiresFirst() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginWathe(LAMP, wathe, false, true);
        state.beginLocal(LAMP, 20L, false, false);

        assertTrue(state.expireLocals(20L).isEmpty());
        assertEquals(SaboteurLightOutageState.WatheEndDecision.RESTORE_AFTER_NATIVE,
                state.endWathe(LAMP, wathe));
        assertEquals(new SaboteurLightOutageState.Restore<>(LAMP, false, true),
                state.finishWatheEnd(LAMP));
    }

    @Test
    void activeLocalLeaseIsThePureGuardForWatheFinalFlicker() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginWathe(LAMP, wathe, true, true);
        state.beginLocal(LAMP, 40L, false, false);

        assertTrue(state.hasLocal(LAMP));
        state.expireLocals(40L);
        assertFalse(state.hasLocal(LAMP));
    }

    @Test
    void watheOnlySourceUsesNativeRestorationAndLeavesNoLease() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginWathe(LAMP, wathe, true, false);

        assertEquals(SaboteurLightOutageState.WatheEndDecision.NATIVE,
                state.endWathe(LAMP, wathe));
        assertEquals(SaboteurLightOutageState.WatheEndDecision.NATIVE,
                state.endWathe(LAMP, wathe));
        assertNull(state.finishWatheEnd(LAMP));
        assertTrue(state.isEmpty());
    }

    @Test
    void repeatedWatheEndIsIdempotentAtTheStateBoundary() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginWathe(LAMP, wathe, true, true);
        state.beginLocal(LAMP, 40L, false, false);

        assertEquals(SaboteurLightOutageState.WatheEndDecision.KEEP_DARK,
                state.endWathe(LAMP, wathe));
        assertEquals(SaboteurLightOutageState.WatheEndDecision.NATIVE,
                state.endWathe(LAMP, wathe));
        assertTrue(state.hasLocal(LAMP));
    }

    @Test
    void roundCleanupRestoresLocalOnlyAndLeavesWatheOwnedLampDark() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginLocal("local", 400L, true, true);
        state.beginWathe(LAMP, wathe, true, true);
        state.beginLocal(LAMP, 400L, false, false);

        assertEquals(List.of(new SaboteurLightOutageState.Restore<>("local", true, true)),
                state.clearLocals());
        assertFalse(state.hasLocal(LAMP));
        assertEquals(SaboteurLightOutageState.WatheEndDecision.RESTORE_AFTER_NATIVE,
                state.endWathe(LAMP, wathe));
        assertEquals(new SaboteurLightOutageState.Restore<>(LAMP, true, true),
                state.finishWatheEnd(LAMP));
        assertTrue(state.isEmpty());
    }

    @Test
    void serverCleanupRestoresEveryLampThatHadALocalLease() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();
        state.beginLocal("first", 400L, true, false);
        state.beginWathe("second", wathe, false, true);
        state.beginLocal("second", 400L, false, false);

        List<SaboteurLightOutageState.Restore<String>> restores = state.clearAll();

        assertEquals(2, restores.size());
        assertTrue(restores.contains(new SaboteurLightOutageState.Restore<>("first", true, false)));
        assertTrue(restores.contains(new SaboteurLightOutageState.Restore<>("second", false, true)));
        assertTrue(state.isEmpty());
    }

    @Test
    void cleanupResetsWatheOnlyWhenAnEphemeralLocalSnapshotIsEntangled() {
        SaboteurLightOutageState<String> state = new SaboteurLightOutageState<>();
        Object wathe = new Object();

        state.beginWathe("global-only", wathe, true, true);
        state.beginLocal("local-only", 400L, true, true);
        assertFalse(state.hasLocalWatheOverlap());

        state.beginLocal("global-only", 400L, false, false);
        assertTrue(state.hasLocalWatheOverlap());
    }
}
