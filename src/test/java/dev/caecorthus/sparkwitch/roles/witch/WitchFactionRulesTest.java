package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.OptionalInt;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchFactionRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void witchFactionForAreaSpellsOnlyMeansGrandWitchAndAccomplice() {
        assertTrue(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.grandWitch()));
        assertTrue(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.accomplice()));

        assertFalse(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.murderousWitch()));
        assertFalse(WitchFactionRules.isWitchFactionMember(SparkWitchRoles.apprenticeWitch()));
        assertFalse(WitchFactionRules.isWitchFactionMember(WatheRoles.CIVILIAN));

        assertFalse(WitchFactionRules.isAffectedByWitchAreaSpell(SparkWitchRoles.grandWitch()));
        assertFalse(WitchFactionRules.isAffectedByWitchAreaSpell(SparkWitchRoles.accomplice()));
        assertTrue(WitchFactionRules.isAffectedByWitchAreaSpell(SparkWitchRoles.murderousWitch()));
        assertTrue(WitchFactionRules.isAffectedByWitchAreaSpell(SparkWitchRoles.apprenticeWitch()));
        assertTrue(WitchFactionRules.isAffectedByWitchAreaSpell(WatheRoles.CIVILIAN));
    }

    @Test
    void fearAffectsEveryoneOutsideGrandWitchFaction() {
        Role fakeNeutral = SparkWitchRoles.murderousWitch();
        Role fakeGrandWitch = SparkWitchRoles.grandWitch();

        assertTrue(WitchFactionRules.isAffectedByFear(SparkWitchRoles.apprenticeWitch()));
        assertTrue(WitchFactionRules.isAffectedByFear(WatheRoles.CIVILIAN));
        assertTrue(WitchFactionRules.isAffectedByFear(fakeNeutral));
        assertFalse(WitchFactionRules.isAffectedByFear(fakeGrandWitch));
        assertFalse(WitchFactionRules.isAffectedByFear(SparkWitchRoles.accomplice()));
        assertFalse(WitchFactionRules.isAffectedByFear(null));
    }

    @Test
    void voodooCurseImmunityOnlyBlocksGrandWitchCurseDeaths() {
        assertTrue(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));

        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.accomplice(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.murderousWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.apprenticeWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.pigGod(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                WatheRoles.CIVILIAN,
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));

        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                Identifier.of(NoellesRoleIds.NAMESPACE, "assassinated")
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                GameConstants.DeathReasons.KNIFE
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                GameConstants.DeathReasons.GUN
        ));
        assertFalse(WitchFactionRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                null
        ));
    }

    @Test
    void grandWitchEconomyRejectsDirectKillsAndPassiveMoney() {
        assertEquals(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
        assertEquals(Boolean.FALSE, WitchFactionRules.economyDecision(
                SparkWitchRoles.grandWitch(),
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
        assertNull(WitchFactionRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
    }

    @Test
    void grandWitchKillMoneyOnlyGoesToLivingAccompliceTeammates() {
        assertTrue(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                false,
                true
        ));

        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                true,
                true
        ));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                false,
                false
        ));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.grandWitch(),
                false,
                true
        ));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.grandWitch(),
                false,
                true
        ));
        assertFalse(WitchFactionRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.murderousWitch(),
                SparkWitchRoles.accomplice(),
                false,
                true
        ));
    }

    @Test
    void accompliceEconomyUsesKillerPassiveAndDirectKillRewardsOnly() {
        assertEquals(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
        assertEquals(Boolean.TRUE, WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
        assertNull(WitchFactionRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.TEAM_KILL
        ));
    }

    @Test
    void grandWitchInstinctColorsCohortOtherWitchesAndEveryoneElse() {
        assertEquals(
                OptionalInt.of(SparkWitchRoles.grandWitch().color()),
                WitchFactionRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(SparkWitchRoles.accomplice().color()),
                WitchFactionRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.accomplice())
        );
        assertEquals(
                OptionalInt.of(0x7AB8FF),
                WitchFactionRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.murderousWitch())
        );
        assertEquals(
                OptionalInt.of(0x7AB8FF),
                WitchFactionRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.apprenticeWitch())
        );
        assertEquals(
                OptionalInt.of(0x36E51B),
                WitchFactionRules.instinctColor(SparkWitchRoles.grandWitch(), WatheRoles.CIVILIAN)
        );
    }

    @Test
    void accompliceInstinctColorsGrandWitchAccompliceAndEveryoneElse() {
        assertEquals(
                OptionalInt.of(SparkWitchRoles.grandWitch().color()),
                WitchFactionRules.instinctColor(SparkWitchRoles.accomplice(), SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(SparkWitchRoles.accomplice().color()),
                WitchFactionRules.instinctColor(SparkWitchRoles.accomplice(), SparkWitchRoles.accomplice())
        );
        assertEquals(
                OptionalInt.of(0x36E51B),
                WitchFactionRules.instinctColor(SparkWitchRoles.accomplice(), WatheRoles.CIVILIAN)
        );
        assertTrue(WitchFactionRules.instinctColor(SparkWitchRoles.murderousWitch(), WatheRoles.CIVILIAN).isEmpty());
    }

    @Test
    void killerStyleInstinctLightOnlyAppliesToGrandWitchFactionMembers() {
        assertTrue(WitchFactionRules.usesKillerStyleInstinctLight(SparkWitchRoles.grandWitch()));
        assertTrue(WitchFactionRules.usesKillerStyleInstinctLight(SparkWitchRoles.accomplice()));

        assertFalse(WitchFactionRules.usesKillerStyleInstinctLight(SparkWitchRoles.apprenticeWitch()));
        assertFalse(WitchFactionRules.usesKillerStyleInstinctLight(SparkWitchRoles.murderousWitch()));
        assertFalse(WitchFactionRules.usesKillerStyleInstinctLight(WatheRoles.CIVILIAN));
        assertFalse(WitchFactionRules.usesKillerStyleInstinctLight(null));
    }

    @Test
    void droppedItemInstinctColorOnlyAppliesToGrandWitchFactionMembers() {
        assertEquals(
                OptionalInt.of(0xDB9D00),
                WitchFactionRules.droppedItemInstinctColor(SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(0xDB9D00),
                WitchFactionRules.droppedItemInstinctColor(SparkWitchRoles.accomplice())
        );

        assertTrue(WitchFactionRules.droppedItemInstinctColor(SparkWitchRoles.apprenticeWitch()).isEmpty());
        assertTrue(WitchFactionRules.droppedItemInstinctColor(SparkWitchRoles.murderousWitch()).isEmpty());
        assertTrue(WitchFactionRules.droppedItemInstinctColor(WatheRoles.CIVILIAN).isEmpty());
        assertTrue(WitchFactionRules.droppedItemInstinctColor(null).isEmpty());
    }

    @Test
    void customInstinctHighlightRequiresLivingViewer() {
        assertTrue(WitchFactionRules.shouldUseCustomInstinctHighlight(true, false));
        assertFalse(WitchFactionRules.shouldUseCustomInstinctHighlight(false, false));
        assertFalse(WitchFactionRules.shouldUseCustomInstinctHighlight(true, true));
    }

    @Test
    void ordinaryWitchInstinctLosesToWatheHardSkips() {
        assertTrue(WitchFactionRules.INSTINCT_PRIORITY < GetInstinctHighlight.HighlightResult.PRIORITY_HIGH);
    }

    @Test
    void obscureOnlyBlocksLivingNonWitchViewers() {
        assertTrue(WitchFactionRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), true, false));
        assertFalse(WitchFactionRules.shouldObscureInstinct(true, SparkWitchRoles.grandWitch(), true, false));
        assertFalse(WitchFactionRules.shouldObscureInstinct(false, SparkWitchRoles.murderousWitch(), true, false));
        assertFalse(WitchFactionRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), false, false));
        assertFalse(WitchFactionRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), true, true));
    }

    @Test
    void fearAndObscureSuppressAffectedInstinctHighlightsOutsideFinalMoment() {
        assertTrue(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, false, SparkWitchRoles.murderousWitch(), true, false, false));
        assertTrue(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                false, true, WatheRoles.KILLER, true, false, false));
        assertTrue(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.apprenticeWitch(), true, false, false));

        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.grandWitch(), true, false, false));
        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.accomplice(), true, false, false));
        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                false, false, SparkWitchRoles.murderousWitch(), true, false, false));
        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.murderousWitch(), false, false, false));
        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.murderousWitch(), true, true, false));
        assertFalse(WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                true, true, SparkWitchRoles.murderousWitch(), true, false, true));
    }

    @Test
    void activeSkillAndSpellTuningMatchGrandWitchPlan() {
        assertEquals(0, WitchFactionRules.DIRECT_KILL_MONEY_REWARD);
        assertEquals(25, WitchFactionRules.WITCH_TEAM_KILL_MONEY_REWARD);
        assertEquals(150, WitchFactionRules.CEREMONIAL_SWORD_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 15), WitchFactionRules.CEREMONIAL_SWORD_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 30), WitchFactionRules.CEREMONIAL_SWORD_COOLDOWN_TICKS);
        assertEquals(0, WitchFactionRules.CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS);
        assertEquals(3, WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS);
        assertEquals(1, WitchFactionRules.CEREMONIAL_SWORD_SPEED_AMPLIFIER);

        assertSpell(WitchFactionRules.GrandWitchSpell.OBSCURE, "sparkwitch_obscure", 80, 30, 120);
        assertSpell(WitchFactionRules.GrandWitchSpell.BLINDNESS, "sparkwitch_blindness", 80, 20, 180);
        assertSpell(WitchFactionRules.GrandWitchSpell.FEAR, "sparkwitch_fear", 50, 10, 300);
        assertSpell(WitchFactionRules.GrandWitchSpell.HEAVINESS, "sparkwitch_heaviness", 60, 10, 180);
    }

    @Test
    void ceremonialSwordTaskUnlockRequiresThreeCompletedTasks() {
        assertFalse(WitchFactionRules.isCeremonialSwordUnlocked(0));
        assertFalse(WitchFactionRules.isCeremonialSwordUnlocked(2));
        assertTrue(WitchFactionRules.isCeremonialSwordUnlocked(3));
        assertTrue(WitchFactionRules.isCeremonialSwordUnlocked(4));

        assertEquals(0, WitchFactionRules.clampCeremonialSwordTaskProgress(-1));
        assertEquals(2, WitchFactionRules.clampCeremonialSwordTaskProgress(2));
        assertEquals(3, WitchFactionRules.clampCeremonialSwordTaskProgress(4));
    }

    private static void assertSpell(
            WitchFactionRules.GrandWitchSpell spell,
            String entryId,
            int manaCost,
            int durationSeconds,
            int cooldownSeconds
    ) {
        assertEquals(entryId, spell.entryId());
        assertEquals(manaCost, spell.manaCost());
        assertEquals(GameConstants.getInTicks(0, durationSeconds), spell.durationTicks());
        assertEquals(GameConstants.getInTicks(cooldownSeconds / 60, cooldownSeconds % 60), spell.cooldownTicks());
        assertEquals(spell, WitchFactionRules.GrandWitchSpell.fromEntryId(entryId));
        assertTrue(WitchFactionRules.isSparkWitchShopSpellId(entryId));
    }
}
