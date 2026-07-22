package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkfactionapi.api.FactionGunPunishmentPolicy;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithRuntimeRulesTest {
    @Test
    void playerIsolationIsBilateralExceptForSelfActions() {
        assertTrue(WraithParticipation.canAffectPlayer(false, false, false));
        assertTrue(WraithParticipation.canAffectPlayer(true, true, true));
        assertFalse(WraithParticipation.canAffectPlayer(true, false, false));
        assertFalse(WraithParticipation.canAffectPlayer(false, true, false));
    }

    @Test
    void windSpiritProjectilePolicyAllowsOnlyOtherOrdinaryLivingParticipants() {
        assertTrue(WraithParticipation.canWindSpiritProjectileAffect(
                true, false, true, true, false, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                false, false, true, true, false, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                true, true, true, true, false, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                true, false, false, true, false, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                true, false, true, false, false, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                true, false, true, true, true, false));
        assertFalse(WraithParticipation.canWindSpiritProjectileAffect(
                true, false, true, true, false, true));
    }

    @Test
    void goodPromotionsNeverReceiveInnocentGunPunishment() {
        assertTrue(WraithParticipation.shouldCancelGunPunishment(
                true,
                true,
                WraithState.Alignment.GOOD,
                FactionGunPunishmentPolicy.Subject.SHOOTER
        ));

        assertFalse(WraithParticipation.shouldCancelGunPunishment(
                true, false, WraithState.Alignment.GOOD, FactionGunPunishmentPolicy.Subject.SHOOTER));
        assertFalse(WraithParticipation.shouldCancelGunPunishment(
                false, true, WraithState.Alignment.GOOD, FactionGunPunishmentPolicy.Subject.SHOOTER));
        assertFalse(WraithParticipation.shouldCancelGunPunishment(
                true, true, WraithState.Alignment.KILLER, FactionGunPunishmentPolicy.Subject.SHOOTER));
        assertFalse(WraithParticipation.shouldCancelGunPunishment(
                true, true, WraithState.Alignment.WITCH, FactionGunPunishmentPolicy.Subject.SHOOTER));
        assertFalse(WraithParticipation.shouldCancelGunPunishment(
                true, true, WraithState.Alignment.GOOD, FactionGunPunishmentPolicy.Subject.VICTIM));
    }

    @Test
    void restrictedFactionUsesOnlyTheSavedAlignment() {
        assertEquals(
                dev.caecorthus.sparkfactionapi.api.FactionIds.CIVILIAN,
                WraithParticipation.restrictedFaction(true, WraithState.Alignment.GOOD)
        );
        assertEquals(
                dev.caecorthus.sparkfactionapi.api.FactionIds.KILLER,
                WraithParticipation.restrictedFaction(true, WraithState.Alignment.KILLER)
        );
        assertEquals(
                dev.caecorthus.sparkwitch.SparkWitchFactions.WITCH,
                WraithParticipation.restrictedFaction(true, WraithState.Alignment.WITCH)
        );
        assertNull(WraithParticipation.restrictedFaction(false, WraithState.Alignment.KILLER));
    }

    @Test
    void fallAndReconnectRulesKeepServerAuthority() {
        assertTrue(WraithLifecycle.shouldTerminateForFall(true, -0.01D, 0.0D));
        assertFalse(WraithLifecycle.shouldTerminateForFall(true, 0.0D, 0.0D));
        assertTrue(WraithLifecycle.shouldResume(true, true, true, true));
        assertFalse(WraithLifecycle.shouldResume(true, true, true, false));
    }
}
