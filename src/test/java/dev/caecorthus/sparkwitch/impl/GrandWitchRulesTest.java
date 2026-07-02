package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchRulesTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void witchFactionForAreaSpellsOnlyMeansGrandWitchAndAccomplice() {
        assertTrue(GrandWitchRules.isWitchFactionMember(SparkWitchRoles.grandWitch()));
        assertTrue(GrandWitchRules.isWitchFactionMember(SparkWitchRoles.accomplice()));

        assertFalse(GrandWitchRules.isWitchFactionMember(SparkWitchRoles.murderousWitch()));
        assertFalse(GrandWitchRules.isWitchFactionMember(SparkWitchRoles.apprenticeWitch()));
        assertFalse(GrandWitchRules.isWitchFactionMember(WatheRoles.CIVILIAN));

        assertFalse(GrandWitchRules.isAffectedByWitchAreaSpell(SparkWitchRoles.grandWitch()));
        assertFalse(GrandWitchRules.isAffectedByWitchAreaSpell(SparkWitchRoles.accomplice()));
        assertTrue(GrandWitchRules.isAffectedByWitchAreaSpell(SparkWitchRoles.murderousWitch()));
        assertTrue(GrandWitchRules.isAffectedByWitchAreaSpell(SparkWitchRoles.apprenticeWitch()));
        assertTrue(GrandWitchRules.isAffectedByWitchAreaSpell(WatheRoles.CIVILIAN));
    }

    @Test
    void fearAffectsEveryoneOutsideGrandWitchFaction() {
        Role fakeNeutral = SparkWitchRoles.murderousWitch();
        Role fakeGrandWitch = SparkWitchRoles.grandWitch();

        assertTrue(GrandWitchRules.isAffectedByFear(SparkWitchRoles.apprenticeWitch()));
        assertTrue(GrandWitchRules.isAffectedByFear(WatheRoles.CIVILIAN));
        assertTrue(GrandWitchRules.isAffectedByFear(fakeNeutral));
        assertFalse(GrandWitchRules.isAffectedByFear(fakeGrandWitch));
        assertFalse(GrandWitchRules.isAffectedByFear(SparkWitchRoles.accomplice()));
        assertFalse(GrandWitchRules.isAffectedByFear(null));
    }

    @Test
    void voodooCurseImmunityOnlyBlocksGrandWitchCurseDeaths() {
        assertTrue(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));

        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.accomplice(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.murderousWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.apprenticeWitch(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.pigGod(),
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                WatheRoles.CIVILIAN,
                NoellesRoleIds.VOODOO_CURSE_DEATH_REASON
        ));

        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                Identifier.of(NoellesRoleIds.NAMESPACE, "assassinated")
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                GameConstants.DeathReasons.KNIFE
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                GameConstants.DeathReasons.GUN
        ));
        assertFalse(GrandWitchRules.shouldBlockVoodooCurse(
                SparkWitchRoles.grandWitch(),
                null
        ));
    }

    @Test
    void grandWitchEconomyRejectsDirectKillsAndPassiveMoney() {
        assertEquals(Boolean.FALSE, GrandWitchRules.economyDecision(
                SparkWitchRoles.grandWitch(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
        assertEquals(Boolean.FALSE, GrandWitchRules.economyDecision(
                SparkWitchRoles.grandWitch(),
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
        assertNull(GrandWitchRules.economyDecision(
                SparkWitchRoles.murderousWitch(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
    }

    @Test
    void grandWitchKillMoneyOnlyGoesToLivingAccompliceTeammates() {
        assertTrue(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                false,
                true
        ));

        assertFalse(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                true,
                true
        ));
        assertFalse(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.accomplice(),
                false,
                false
        ));
        assertFalse(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.grandWitch(),
                SparkWitchRoles.grandWitch(),
                false,
                true
        ));
        assertFalse(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.accomplice(),
                SparkWitchRoles.grandWitch(),
                false,
                true
        ));
        assertFalse(GrandWitchRules.shouldAwardWitchTeamKillMoney(
                SparkWitchRoles.murderousWitch(),
                SparkWitchRoles.accomplice(),
                false,
                true
        ));
    }

    @Test
    void accompliceEconomyUsesKillerPassiveAndDirectKillRewardsOnly() {
        assertEquals(Boolean.TRUE, GrandWitchRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.PASSIVE
        ));
        assertEquals(Boolean.TRUE, GrandWitchRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.DIRECT_KILL
        ));
        assertNull(GrandWitchRules.economyDecision(
                SparkWitchRoles.accomplice(),
                FactionEconomyPolicy.RewardKind.TEAM_KILL
        ));
    }

    @Test
    void grandWitchInstinctColorsCohortOtherWitchesAndEveryoneElse() {
        assertEquals(
                OptionalInt.of(SparkWitchRoles.grandWitch().color()),
                GrandWitchRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(SparkWitchRoles.accomplice().color()),
                GrandWitchRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.accomplice())
        );
        assertEquals(
                OptionalInt.of(0x7AB8FF),
                GrandWitchRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.murderousWitch())
        );
        assertEquals(
                OptionalInt.of(0x7AB8FF),
                GrandWitchRules.instinctColor(SparkWitchRoles.grandWitch(), SparkWitchRoles.apprenticeWitch())
        );
        assertEquals(
                OptionalInt.of(0x36E51B),
                GrandWitchRules.instinctColor(SparkWitchRoles.grandWitch(), WatheRoles.CIVILIAN)
        );
    }

    @Test
    void accompliceInstinctColorsGrandWitchAccompliceAndEveryoneElse() {
        assertEquals(
                OptionalInt.of(SparkWitchRoles.grandWitch().color()),
                GrandWitchRules.instinctColor(SparkWitchRoles.accomplice(), SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(SparkWitchRoles.accomplice().color()),
                GrandWitchRules.instinctColor(SparkWitchRoles.accomplice(), SparkWitchRoles.accomplice())
        );
        assertEquals(
                OptionalInt.of(0x36E51B),
                GrandWitchRules.instinctColor(SparkWitchRoles.accomplice(), WatheRoles.CIVILIAN)
        );
        assertTrue(GrandWitchRules.instinctColor(SparkWitchRoles.murderousWitch(), WatheRoles.CIVILIAN).isEmpty());
    }

    @Test
    void killerStyleInstinctLightOnlyAppliesToGrandWitchFactionMembers() {
        assertTrue(GrandWitchRules.usesKillerStyleInstinctLight(SparkWitchRoles.grandWitch()));
        assertTrue(GrandWitchRules.usesKillerStyleInstinctLight(SparkWitchRoles.accomplice()));

        assertFalse(GrandWitchRules.usesKillerStyleInstinctLight(SparkWitchRoles.apprenticeWitch()));
        assertFalse(GrandWitchRules.usesKillerStyleInstinctLight(SparkWitchRoles.murderousWitch()));
        assertFalse(GrandWitchRules.usesKillerStyleInstinctLight(WatheRoles.CIVILIAN));
        assertFalse(GrandWitchRules.usesKillerStyleInstinctLight(null));
    }

    @Test
    void droppedItemInstinctColorOnlyAppliesToGrandWitchFactionMembers() {
        assertEquals(
                OptionalInt.of(0xDB9D00),
                GrandWitchRules.droppedItemInstinctColor(SparkWitchRoles.grandWitch())
        );
        assertEquals(
                OptionalInt.of(0xDB9D00),
                GrandWitchRules.droppedItemInstinctColor(SparkWitchRoles.accomplice())
        );

        assertTrue(GrandWitchRules.droppedItemInstinctColor(SparkWitchRoles.apprenticeWitch()).isEmpty());
        assertTrue(GrandWitchRules.droppedItemInstinctColor(SparkWitchRoles.murderousWitch()).isEmpty());
        assertTrue(GrandWitchRules.droppedItemInstinctColor(WatheRoles.CIVILIAN).isEmpty());
        assertTrue(GrandWitchRules.droppedItemInstinctColor(null).isEmpty());
    }

    @Test
    void customInstinctHighlightRequiresLivingViewer() {
        assertTrue(GrandWitchRules.shouldUseCustomInstinctHighlight(true, false));
        assertFalse(GrandWitchRules.shouldUseCustomInstinctHighlight(false, false));
        assertFalse(GrandWitchRules.shouldUseCustomInstinctHighlight(true, true));
    }

    @Test
    void ordinaryWitchInstinctLosesToWatheHardSkips() {
        assertTrue(GrandWitchRules.INSTINCT_PRIORITY < GetInstinctHighlight.HighlightResult.PRIORITY_HIGH);
    }

    @Test
    void obscureOnlyBlocksLivingNonWitchViewers() {
        assertTrue(GrandWitchRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), true, false));
        assertFalse(GrandWitchRules.shouldObscureInstinct(true, SparkWitchRoles.grandWitch(), true, false));
        assertFalse(GrandWitchRules.shouldObscureInstinct(false, SparkWitchRoles.murderousWitch(), true, false));
        assertFalse(GrandWitchRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), false, false));
        assertFalse(GrandWitchRules.shouldObscureInstinct(true, SparkWitchRoles.murderousWitch(), true, true));
    }

    @Test
    void activeSkillAndSpellTuningMatchGrandWitchPlan() {
        assertEquals(0, GrandWitchRules.DIRECT_KILL_MONEY_REWARD);
        assertEquals(25, GrandWitchRules.WITCH_TEAM_KILL_MONEY_REWARD);
        assertEquals(150, GrandWitchRules.CEREMONIAL_SWORD_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 15), GrandWitchRules.CEREMONIAL_SWORD_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 30), GrandWitchRules.CEREMONIAL_SWORD_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), GrandWitchRules.CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS);
        assertEquals(1, GrandWitchRules.CEREMONIAL_SWORD_SPEED_AMPLIFIER);

        assertSpell(GrandWitchRules.GrandWitchSpell.OBSCURE, "sparkwitch_obscure", 80, 30, 120);
        assertSpell(GrandWitchRules.GrandWitchSpell.BLINDNESS, "sparkwitch_blindness", 80, 20, 180);
        assertSpell(GrandWitchRules.GrandWitchSpell.FEAR, "sparkwitch_fear", 50, 10, 300);
        assertSpell(GrandWitchRules.GrandWitchSpell.HEAVINESS, "sparkwitch_heaviness", 60, 10, 180);
    }

    private static void assertSpell(
            GrandWitchRules.GrandWitchSpell spell,
            String entryId,
            int manaCost,
            int durationSeconds,
            int cooldownSeconds
    ) {
        assertEquals(entryId, spell.entryId());
        assertEquals(manaCost, spell.manaCost());
        assertEquals(GameConstants.getInTicks(0, durationSeconds), spell.durationTicks());
        assertEquals(GameConstants.getInTicks(cooldownSeconds / 60, cooldownSeconds % 60), spell.cooldownTicks());
        assertEquals(spell, GrandWitchRules.GrandWitchSpell.fromEntryId(entryId));
        assertTrue(GrandWitchRules.isSparkWitchShopSpellId(entryId));
    }
}
