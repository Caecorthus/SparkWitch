package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintRulesTest {
    @Test
    void exposesApprovedRoleAndHellfireTuning() {
        assertEquals(0xEEBC78, SaintRules.COLOR);
        assertEquals(1200, SaintRules.HELLFIRE_INITIAL_COOLDOWN_TICKS);
        assertEquals(300, SaintRules.HELLFIRE_ACTIVE_TICKS);
        assertEquals(1200, SaintRules.HELLFIRE_POST_COOLDOWN_TICKS);
    }

    @Test
    void currentBomberReceivesFourHundredKarmaWhileOtherRolesReceiveOneHundred() {
        Role bomber = role(Identifier.of("noellesroles", "bomber"), false, true);
        Role otherKiller = role(Identifier.of("wathe", "killer"), false, true);

        assertTrue(SaintRules.isBomber(bomber));
        assertEquals(400, SaintRules.karmaFor(bomber));
        assertEquals(100, SaintRules.karmaFor(otherKiller));
    }

    @Test
    void grandWitchAloneIsImmuneToKarma() {
        Role grandWitch = role(Identifier.of("sparkwitch", "grand_witch"), false, true);
        Role accomplice = role(Identifier.of("sparkwitch", "accomplice"), false, true);
        Role bomber = role(Identifier.of("noellesroles", "bomber"), false, true);

        assertTrue(SaintRules.isKarmaImmune(grandWitch));
        assertFalse(SaintRules.isKarmaImmune(accomplice));
        assertFalse(SaintRules.isKarmaImmune(bomber));
        assertFalse(SaintRules.isKarmaImmune(null));
        assertEquals(0, SaintRules.effectiveKarmaTicks(grandWitch, 400));
        assertEquals(400, SaintRules.effectiveKarmaTicks(accomplice, 400));
    }

    @Test
    void identifiesSaintAndBomberByStableRoleIds() {
        Role saint = role(Identifier.of("sparkwitch", "saint"), true, false);
        Role bomber = role(Identifier.of("noellesroles", "bomber"), false, true);
        Role otherRole = role(Identifier.of("sparkwitch", "apprentice_witch"), true, false);

        assertTrue(SaintRules.isSaint(saint));
        assertTrue(SaintRules.isBomber(bomber));
        assertFalse(SaintRules.isSaint(otherRole));
        assertFalse(SaintRules.isBomber(otherRole));
        assertFalse(SaintRules.isSaint(null));
        assertFalse(SaintRules.isBomber(null));
    }

    @Test
    void karmaRecordsAcceptOnlyTimedBombTransferOrPoisonNeedle() {
        Identifier timedBomb = Identifier.of("noellesroles", "timed_bomb");
        Identifier poisonNeedle = Identifier.of("noellesroles", "poison_needle");

        assertTrue(SaintRules.isKarmaRecordTrigger(timedBomb, "transfer"));
        assertTrue(SaintRules.isKarmaRecordTrigger(poisonNeedle, null));
        assertTrue(SaintRules.isKarmaRecordTrigger(poisonNeedle, "inject"));
        assertFalse(SaintRules.isKarmaRecordTrigger(timedBomb, "place"));
        assertFalse(SaintRules.isKarmaRecordTrigger(timedBomb, "Transfer"));
        assertFalse(SaintRules.isKarmaRecordTrigger(timedBomb, null));
        assertFalse(SaintRules.isKarmaRecordTrigger(Identifier.of("wathe", "knife"), "transfer"));
    }

    @Test
    void cooldownMergeNeverShortensAnExistingCooldown() {
        assertEquals(1200, SaintRules.mergeCooldownTicks(1200, 300));
        assertEquals(1200, SaintRules.mergeCooldownTicks(300, 1200));
        assertEquals(1200, SaintRules.mergeCooldownTicks(1200, 1200));
    }

    @Test
    void blocksOnlyTheNoellesRolesVoodooDeathReason() {
        assertTrue(SaintRules.isBlockedDeathReason(Identifier.of("noellesroles", "voodoo")));
        assertFalse(SaintRules.isBlockedDeathReason(Identifier.of("wathe", "shot")));
        assertFalse(SaintRules.isBlockedDeathReason(Identifier.of("sparkwitch", "voodoo")));
        assertFalse(SaintRules.isBlockedDeathReason(null));
    }

    @Test
    void blocksEffectiveCivilianAndExactVoodooKillsOnlyForSaintVictims() {
        Identifier shot = Identifier.of("wathe", "shot");
        Identifier killerFaction = Identifier.of("sparkfactionapi", "killer");

        assertTrue(SaintRules.blocksKill(true, FactionIds.CIVILIAN, shot));
        assertTrue(SaintRules.blocksKill(true, null, Identifier.of("noellesroles", "voodoo")));
        assertFalse(SaintRules.blocksKill(true, killerFaction, shot));
        assertFalse(SaintRules.blocksKill(true, killerFaction, Identifier.of("sparkwitch", "voodoo")));
        assertFalse(SaintRules.blocksKill(false, FactionIds.CIVILIAN,
                Identifier.of("noellesroles", "voodoo")));
    }

    private static Role role(Identifier id, boolean innocent, boolean canUseKiller) {
        return new Role(id, 0, innocent, canUseKiller, Role.MoodType.FAKE, -1, false);
    }
}
