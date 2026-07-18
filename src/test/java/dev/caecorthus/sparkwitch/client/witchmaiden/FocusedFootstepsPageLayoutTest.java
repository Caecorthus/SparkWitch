package dev.caecorthus.sparkwitch.client.witchmaiden;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsPageLayoutTest {
    @Test
    void clampsTheApprovedZeroOneTenElevenAndTwentyPlayerCases() {
        assertWindow(0, 4, 0, 0, 0, false, false);
        assertWindow(1, 4, 0, 0, 1, false, false);
        assertWindow(10, 4, 0, 0, 10, false, false);
        assertWindow(11, 0, 0, 0, 10, false, true);
        assertWindow(11, 1, 1, 10, 11, true, false);
        assertWindow(20, 9, 1, 10, 20, true, false);
    }

    @Test
    void centersPlayersAtThirtySixPixelIntervalsAndWrapsControlsToTheirOwnRow() {
        assertEquals(10, FocusedFootstepsPageLayout.PLAYERS_PER_PAGE);
        assertEquals(36, FocusedFootstepsPageLayout.SLOT_SPACING);

        int firstPageStart = FocusedFootstepsPageLayout.playerStartX(500, 10);
        assertEquals(80, firstPageStart);
        assertEquals(404, FocusedFootstepsPageLayout.playerX(500, 9, 10));

        int lastPageStart = FocusedFootstepsPageLayout.playerStartX(500, 1);
        assertEquals(242, lastPageStart);
        assertTrue(FocusedFootstepsPageLayout.controlRowY(240)
                < FocusedFootstepsPageLayout.rowY(240));
    }

    @Test
    void width341FitsElevenPlayersWithoutChangingPlayerSpacing() {
        assertFitsWidth341(11, 0, 10, false, true);
        assertFitsWidth341(11, 1, 1, true, false);
    }

    @Test
    void width341FitsBothFullTwentyPlayerPagesWithoutChangingPlayerSpacing() {
        assertFitsWidth341(20, 0, 10, false, true);
        assertFitsWidth341(20, 1, 10, true, false);
    }

    private static void assertWindow(
            int candidates,
            int requestedPage,
            int page,
            int start,
            int end,
            boolean previous,
            boolean next
    ) {
        FocusedFootstepsPageLayout.PageWindow window =
                FocusedFootstepsPageLayout.window(candidates, requestedPage);
        assertEquals(page, window.page());
        assertEquals(start, window.startIndex());
        assertEquals(end, window.endIndex());
        assertEquals(previous, window.showPrevious());
        assertEquals(next, window.showNext());
    }

    private static void assertFitsWidth341(
            int candidates,
            int page,
            int visibleCount,
            boolean previous,
            boolean next
    ) {
        FocusedFootstepsPageLayout.PageWindow window =
                FocusedFootstepsPageLayout.window(candidates, page);
        assertEquals(visibleCount, window.visibleCount());
        assertEquals(previous, window.showPrevious());
        assertEquals(next, window.showNext());

        int priorX = -FocusedFootstepsPageLayout.SLOT_SPACING;
        for (int index = 0; index < visibleCount; index++) {
            int x = FocusedFootstepsPageLayout.playerX(341, index, visibleCount);
            assertTrue(x >= 0);
            assertTrue(x + 16 <= 341);
            if (index > 0) {
                assertEquals(FocusedFootstepsPageLayout.SLOT_SPACING, x - priorX);
            }
            priorX = x;
        }

        int controls = (previous ? 1 : 0) + (next ? 1 : 0);
        int controlStart = FocusedFootstepsPageLayout.controlStartX(341, controls);
        assertTrue(controlStart >= 0);
        assertTrue(controlStart + Math.max(0, controls - 1) * FocusedFootstepsPageLayout.SLOT_SPACING + 16 <= 341);
    }
}
