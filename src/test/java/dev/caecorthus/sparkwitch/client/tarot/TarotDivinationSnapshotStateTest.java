package dev.caecorthus.sparkwitch.client.tarot;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarotDivinationSnapshotStateTest {
    @Test
    void repurchaseOverwritesThePersistentSnapshot() {
        TarotDivinationSnapshotState state = new TarotDivinationSnapshotState();

        state.overwrite(4, 2, 1, 2);
        state.overwrite(3, 1, 1, 2);

        assertEquals(new TarotDivinationSnapshotState.Snapshot(3, 1, 1, 2), state.snapshot().orElseThrow());
    }

    @Test
    void invalidRoundOrRoleClearsTheSnapshot() {
        TarotDivinationSnapshotState state = new TarotDivinationSnapshotState();
        state.overwrite(4, 2, 1, 2);

        assertTrue(state.retainFor(true, true, true));
        assertFalse(state.retainFor(true, false, true));
        assertTrue(state.snapshot().isEmpty());

        state.overwrite(4, 2, 1, 2);
        assertFalse(state.retainFor(true, true, false));
        assertTrue(state.snapshot().isEmpty());
    }

    @Test
    void hudRowsKeepTheApprovedFactionOrder() {
        TarotDivinationSnapshotState.Snapshot snapshot =
                new TarotDivinationSnapshotState.Snapshot(4, 2, 1, 2);

        assertEquals(
                List.of(
                        new TarotDivinationHudLayout.Row(TarotDivinationHudLayout.FactionSlot.CIVILIAN, 4),
                        new TarotDivinationHudLayout.Row(TarotDivinationHudLayout.FactionSlot.KILLER, 2),
                        new TarotDivinationHudLayout.Row(TarotDivinationHudLayout.FactionSlot.NEUTRAL, 1),
                        new TarotDivinationHudLayout.Row(TarotDivinationHudLayout.FactionSlot.WITCH, 2)
                ),
                TarotDivinationHudLayout.rows(snapshot)
        );
    }

    @Test
    void hudStartsBelowWatheMoneyAndAdvancesOneStableRowAtATime() {
        assertEquals(20, TarotDivinationHudLayout.titleY(9));
        assertEquals(31, TarotDivinationHudLayout.rowY(0, 9));
        assertEquals(42, TarotDivinationHudLayout.rowY(1, 9));
    }
}
