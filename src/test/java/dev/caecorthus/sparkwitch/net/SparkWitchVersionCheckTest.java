package dev.caecorthus.sparkwitch.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchVersionCheckTest {
    @Test
    void matchingVersionsAreCompatible() {
        assertTrue(SparkWitchVersionCheck.isCompatible("0.1.5.4", "0.1.5.4"));
    }

    @Test
    void differentOrBlankVersionsAreRejected() {
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.4", "0.1.5.3"));
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.4", ""));
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.4", null));
    }

    @Test
    void unansweredLoginQueriesAreAllowedForProxyTransfers() {
        assertFalse(SparkWitchVersionCheck.shouldRejectUnansweredLoginQuery());
    }

    @Test
    void disconnectMessagesNameExpectedAndActualVersions() {
        assertEquals(
                "SparkWitch is required on the client with version 0.1.5.4.",
                SparkWitchVersionCheck.missingClientMessage("0.1.5.4")
        );
        assertEquals(
                "SparkWitch version mismatch: server=0.1.5.4, client=0.1.5.3. "
                        + "Please install the same SparkWitch version as the server.",
                SparkWitchVersionCheck.mismatchMessage("0.1.5.4", "0.1.5.3")
        );
    }
}
