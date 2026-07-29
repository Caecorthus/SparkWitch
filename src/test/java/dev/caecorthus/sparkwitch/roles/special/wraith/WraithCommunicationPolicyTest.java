package dev.caecorthus.sparkwitch.roles.special.wraith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WraithCommunicationPolicyTest {
    @Test
    void guardianAngelAndCreativeModeAreTheOnlyCommunicationExceptions() {
        assertFalse(WraithCommunicationPolicy.mayCommunicate(true, false, false));
        assertTrue(WraithCommunicationPolicy.mayCommunicate(true, true, false));
        assertTrue(WraithCommunicationPolicy.mayCommunicate(true, false, true));
        assertTrue(WraithCommunicationPolicy.mayCommunicate(false, false, false));
    }

    @Test
    void onlyActiveGuardianAngelJoinsDeadVoiceGroup() {
        assertFalse(WraithCommunicationPolicy.usesDeadVoiceGroup(true, false));
        assertTrue(WraithCommunicationPolicy.usesDeadVoiceGroup(true, true));
        assertFalse(WraithCommunicationPolicy.usesDeadVoiceGroup(false, true));
    }
}
