package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
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

    @Test
    void grandWitchAndAccompliceHardSkipOnlyInvisiblePhantoms() {
        Role phantom = role(NoellesRoleIds.PHANTOM);
        Role otherNoellesRole = role(Identifier.of(NoellesRoleIds.NAMESPACE, "shadow_jester"));

        assertEquals(1_000, WitchFactionRules.HIDDEN_PHANTOM_SKIP_PRIORITY);
        assertTrue(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.grandWitch(), phantom, true));
        assertTrue(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.accomplice(), phantom, true));
        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.grandWitch(), phantom, false));
        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.accomplice(), otherNoellesRole, true));
    }

    @Test
    void hiddenPhantomVetoDoesNotApplyToOtherSparkWitchViewers() {
        Role phantom = role(NoellesRoleIds.PHANTOM);

        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.apprenticeWitch(), phantom, true));
        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.pigGod(), phantom, true));
        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.murderousWitch(), phantom, true));
    }

    private static Role role(Identifier id) {
        return new Role(id, 0, false, false, Role.MoodType.FAKE, -1, false);
    }
}
