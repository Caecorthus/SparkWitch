package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictedWraithInstinctPolicyTest {
    @Test
    void restrictedWraithBlocksGoodAndKillerFactionOutlines() {
        FactionInstinctPolicy.InstinctResult goodFallback =
                FactionInstinctPolicy.InstinctResult.show(0x36E51B, true, 90);
        FactionInstinctPolicy.InstinctResult killerFallback =
                FactionInstinctPolicy.InstinctResult.show(0xFF0000, true, 100);

        FactionInstinctPolicy.InstinctResult result =
                WitchInstinctPolicy.restrictedWraithHighlight(true);

        assertNotNull(result);
        assertTrue(result.skip());
        assertTrue(result.priority() > goodFallback.priority());
        assertTrue(result.priority() > killerFallback.priority());
    }
}
