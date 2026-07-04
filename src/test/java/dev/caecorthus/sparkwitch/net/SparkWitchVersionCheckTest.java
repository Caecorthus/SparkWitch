package dev.caecorthus.sparkwitch.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchVersionCheckTest {
    @Test
    void matchingVersionsAreCompatible() {
        assertTrue(SparkWitchVersionCheck.isCompatible("0.1.5.1", "0.1.5.1"));
    }

    @Test
    void differentOrBlankVersionsAreRejected() {
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.1", "0.1.5"));
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.1", ""));
        assertFalse(SparkWitchVersionCheck.isCompatible("0.1.5.1", null));
    }

    @Test
    void unansweredLoginQueriesAreAllowedForProxyTransfers() {
        assertFalse(SparkWitchVersionCheck.shouldRejectUnansweredLoginQuery());
    }

    @Test
    void disconnectMessagesNameExpectedAndActualVersions() {
        assertEquals(
                "SparkWitch is required on the client with version 0.1.5.1.",
                SparkWitchVersionCheck.missingClientMessage("0.1.5.1")
        );
        assertEquals(
                "SparkWitch version mismatch: server=0.1.5.1, client=0.1.5. "
                        + "Please install the same SparkWitch version as the server.",
                SparkWitchVersionCheck.mismatchMessage("0.1.5.1", "0.1.5")
        );
    }
}
