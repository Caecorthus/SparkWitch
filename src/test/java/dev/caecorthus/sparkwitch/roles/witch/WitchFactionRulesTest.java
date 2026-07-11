package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchFactionRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void economyDecisionsKeepRoleSpecificKillAndPassiveRewards() {
        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
        assertSame(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(), FactionEconomyPolicy.RewardKind.PASSIVE));
        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(), FactionEconomyPolicy.RewardKind.PASSIVE));
        assertNull(WitchFactionRules.economyDecision(
                SparkWitchRoles.murderousWitch(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
    }

    @Test
    void teamKillRewardStillRequiresALivingAccompliceTeammate() {
        assertEquals(25, WitchFactionRules.WITCH_TEAM_KILL_MONEY_REWARD);
        assertTrue(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(), SparkWitchRoles.accomplice(), false, true));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(), SparkWitchRoles.accomplice(), false, false));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(), SparkWitchRoles.accomplice(), true, true));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(), SparkWitchRoles.grandWitch(), false, true));
    }
}
