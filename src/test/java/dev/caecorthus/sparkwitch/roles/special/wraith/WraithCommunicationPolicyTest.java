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
    void promotedWraithNoLongerUsesTheOrdinaryWraithCommunicationBlock() {
        assertTrue(WraithCommunicationPolicy.shouldBlockCommunication(true, false, false, false));
        assertFalse(WraithCommunicationPolicy.shouldBlockCommunication(true, true, false, false));
        assertFalse(WraithCommunicationPolicy.shouldBlockCommunication(true, true, true, false));
    }

    @Test
    void promotedCivilianWraithsKeepWalkieTalkieButLoseLivingProximityOutput() {
        assertTrue(WraithCommunicationPolicy.shouldBlockPromotedCivilianVoiceToLiving(
                true, true, false, false, false, false, true
        ));
        assertFalse(WraithCommunicationPolicy.shouldBlockPromotedCivilianVoiceToLiving(
                true, true, false, false, false, true, true
        ));
        assertFalse(WraithCommunicationPolicy.shouldBlockPromotedCivilianVoiceToLiving(
                true, true, false, false, false, false, false
        ));
        assertFalse(WraithCommunicationPolicy.shouldBlockPromotedCivilianVoiceToLiving(
                true, true, false, true, false, false, true
        ));
    }

    @Test
    void promotedCurserCanSpeakToLivingWitchFactionMembers() {
        assertTrue(WraithCommunicationPolicy.shouldAllowPromotedCurserVoiceToLivingWitchFaction(
                true, true, true, false, true, true
        ));
        assertFalse(WraithCommunicationPolicy.shouldAllowPromotedCurserVoiceToLivingWitchFaction(
                true, true, true, false, true, false
        ));
        assertFalse(WraithCommunicationPolicy.shouldAllowPromotedCurserVoiceToLivingWitchFaction(
                true, true, false, false, true, true
        ));
        assertFalse(WraithCommunicationPolicy.shouldAllowPromotedCurserVoiceToLivingWitchFaction(
                true, false, true, false, true, true
        ));
    }

    @Test
    void guardianAngelOnlyLosesOutputToWatheLivingRecipients() {
        assertTrue(WraithCommunicationPolicy.shouldBlockGuardianAngelVoiceToLiving(
                true, true, true, true
        ));
        assertFalse(WraithCommunicationPolicy.shouldBlockGuardianAngelVoiceToLiving(
                true, true, true, false
        ));
        assertFalse(WraithCommunicationPolicy.shouldBlockGuardianAngelVoiceToLiving(
                true, false, true, true
        ));
    }

    @Test
    void onlyActiveGuardianAngelJoinsDeadVoiceGroup() {
        assertFalse(WraithCommunicationPolicy.usesDeadVoiceGroup(true, false));
        assertTrue(WraithCommunicationPolicy.usesDeadVoiceGroup(true, true));
        assertFalse(WraithCommunicationPolicy.usesDeadVoiceGroup(false, true));
    }
}
