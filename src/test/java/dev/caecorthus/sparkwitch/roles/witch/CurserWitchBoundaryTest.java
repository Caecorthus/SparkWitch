package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.WitchPoisonVisionRules;
import dev.caecorthus.sparkwitch.mana.WitchManaRules;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.skill.WitchSkillPresentationRules;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurserWitchBoundaryTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void includesCurserOnlyInGenericWitchFactionBehavior() {
        assertTrue(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.isAffectedByWitchAreaSpell(SparkWitchRoles.curser()));
        assertTrue(SparkFactionApi.capabilities(SparkWitchFactions.WITCH).hasBlackoutImmunity());
        assertTrue(SparkFactionApi.capabilities(SparkWitchFactions.WITCH).sharesCohort());

        assertFalse(WitchFactionRules.isGrandWitch(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.isAccomplice(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.usesKillerStyleInstinctLight(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.shouldHardSkipInvisiblePhantom(
                SparkWitchRoles.curser(), null, true));
        assertTrue(WitchFactionRules.droppedItemInstinctColor(SparkWitchRoles.curser()).isEmpty());
    }

    @Test
    void deniesCurserEconomyRewardsBeforeWitchFactionFallback() {
        assertTrue(SparkFactionApi.capabilities(SparkWitchFactions.WITCH).receivesKillerPassiveMoney());
        assertTrue(SparkFactionApi.capabilities(SparkWitchFactions.WITCH).receivesKillRewards());
        assertSame(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.curser(), FactionEconomyPolicy.RewardKind.PASSIVE));
        assertSame(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.curser(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
        assertNull(WitchFactionRules.economyDecision(
                SparkWitchRoles.curser(), FactionEconomyPolicy.RewardKind.TASK));
        assertNull(WitchFactionRules.economyDecision(
                SparkWitchRoles.curser(), FactionEconomyPolicy.RewardKind.TEAM_KILL));

        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
        assertSame(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(), FactionEconomyPolicy.RewardKind.PASSIVE));
        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(), FactionEconomyPolicy.RewardKind.DIRECT_KILL));
        assertSame(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(), FactionEconomyPolicy.RewardKind.PASSIVE));
    }

    @Test
    void rejectsCurserFromManaShopLoadoutPoisonAndSkillPresentation() {
        assertFalse(WitchManaRules.isManaRole(SparkWitchRoles.curser()));
        assertFalse(WitchManaRules.canRegenerateNaturally(SparkWitchRoles.curser()));
        assertFalse(WitchPoisonVisionRules.canSeeHiddenPoison(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.isGrandWitch(SparkWitchRoles.curser()));
        assertFalse(WitchFactionRules.isAccomplice(SparkWitchRoles.curser()));

        registeredSkillIds().forEach(skillId -> assertFalse(
                WitchSkillPresentationRules.shouldShowInventorySkillPanel(SparkWitchRoles.curser(), skillId)));
    }

    @Test
    void inventoryPanelAcceptsOnlyEachApprovedRolesOwnSkills() {
        Identifier grandWitchSkill = GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID;
        Identifier apprenticeSkill = ApprenticeAbilityCatalog.ABILITY_IDS.getFirst();
        Identifier murderousWitchSkill = MurderousWitchDeathRayRules.DEATH_RAY_ID;

        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(), grandWitchSkill));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(), apprenticeSkill));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.grandWitch(), murderousWitchSkill));

        for (Identifier skillId : ApprenticeAbilityCatalog.ABILITY_IDS) {
            assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                    SparkWitchRoles.apprenticeWitch(), skillId));
        }
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(), grandWitchSkill));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.apprenticeWitch(), murderousWitchSkill));

        assertTrue(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(), murderousWitchSkill));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(), grandWitchSkill));
        assertFalse(WitchSkillPresentationRules.shouldShowInventorySkillPanel(
                SparkWitchRoles.murderousWitch(), apprenticeSkill));
    }

    private static Stream<Identifier> registeredSkillIds() {
        return Stream.concat(
                Stream.of(
                        GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                        MurderousWitchDeathRayRules.DEATH_RAY_ID
                ),
                ApprenticeAbilityCatalog.ABILITY_IDS.stream()
        );
    }
}
