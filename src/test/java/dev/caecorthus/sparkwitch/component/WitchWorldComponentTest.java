package dev.caecorthus.sparkwitch.component;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchWorldComponentTest {
    @Test
    void grandWitchCeremonialSwordBgmTracksOverlappingSources() {
        GrandWitchCeremonialSwordBgmSources sources = new GrandWitchCeremonialSwordBgmSources();
        UUID firstGrandWitch = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondGrandWitch = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertTrue(sources.start(firstGrandWitch));
        assertFalse(sources.start(firstGrandWitch));
        assertTrue(sources.start(secondGrandWitch));

        assertTrue(sources.isActive());
        assertEquals(2, sources.size());

        assertTrue(sources.stop(firstGrandWitch));

        assertTrue(sources.isActive());
        assertEquals(1, sources.size());

        assertTrue(sources.stop(secondGrandWitch));

        assertFalse(sources.isActive());
        assertEquals(0, sources.size());
    }

    @Test
    void clearRoundStateStopsGrandWitchCeremonialSwordBgm() {
        GrandWitchCeremonialSwordBgmSources sources = new GrandWitchCeremonialSwordBgmSources();
        sources.start(UUID.fromString("00000000-0000-0000-0000-000000000003"));

        sources.clear();

        assertFalse(sources.isActive());
        assertEquals(0, sources.size());
    }
}
