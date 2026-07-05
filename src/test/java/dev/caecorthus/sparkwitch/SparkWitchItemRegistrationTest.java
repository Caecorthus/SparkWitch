package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordCombatService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import net.minecraft.util.ActionResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchItemRegistrationTest {
    private static final Path CEREMONIAL_SWORD_ITEM_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/item/ceremonialsword/CeremonialSwordItem.java");

    @Test
    void ceremonialSwordUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.CEREMONIAL_SWORD_ID.getNamespace());
        assertEquals("ceremonial_sword", SparkWitchItems.CEREMONIAL_SWORD_ID.getPath());
    }

    @Test
    void firePokerUsesSparkWitchItemId() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchItems.FIRE_POKER_ID.getNamespace());
        assertEquals("fire_poker", SparkWitchItems.FIRE_POKER_ID.getPath());
    }

    @Test
    void ceremonialSwordDashUsesPlannedTuning() {
        assertEquals(100, CeremonialSwordItem.DASH_COOLDOWN_TICKS);
        assertEquals(6.0, CeremonialSwordDashService.DASH_DISTANCE_BLOCKS);
    }

    @Test
    void ceremonialSwordKeepsDamageWithoutVanillaAttackCooldown() throws IOException {
        assertEquals(16, CeremonialSwordItem.ATTACK_DAMAGE);
        assertEquals(13, CeremonialSwordItem.ATTACK_DAMAGE_BONUS_VALUE);

        String source = Files.readString(CEREMONIAL_SWORD_ITEM_SOURCE);
        assertTrue(source.contains("EntityAttributes.GENERIC_ATTACK_DAMAGE"));
        assertTrue(source.contains("BASE_ATTACK_DAMAGE_MODIFIER_ID"));
        assertFalse(source.contains("EntityAttributes.GENERIC_ATTACK_SPEED"));
        assertFalse(source.contains("BASE_ATTACK_SPEED_MODIFIER_ID"));
    }

    @Test
    void ceremonialSwordCustomAttackIgnoresVanillaCooldownForZeroSecondLeftClick() {
        CeremonialSwordCombatService.AttackDecision earlySwing =
                CeremonialSwordCombatService.decideAttack(true, false);
        assertTrue(earlySwing.handled());
        assertFalse(earlySwing.resetVanillaCooldown());
        assertTrue(earlySwing.kill());

        CeremonialSwordCombatService.AttackDecision fullSwing =
                CeremonialSwordCombatService.decideAttack(true, true);
        assertTrue(fullSwing.handled());
        assertFalse(fullSwing.resetVanillaCooldown());
        assertTrue(fullSwing.kill());
    }

    @Test
    void ceremonialSwordTargetGateDoesNotRequireRoundBoundOrSurvivalAttacker() {
        assertTrue(CeremonialSwordCombatService.canStrikeTarget(false, true, true));
        assertFalse(CeremonialSwordCombatService.canStrikeTarget(true, true, true));
        assertFalse(CeremonialSwordCombatService.canStrikeTarget(false, false, true));
        assertFalse(CeremonialSwordCombatService.canStrikeTarget(false, true, false));
    }

    @Test
    void ceremonialSwordClientAttackCancelsOnlyPlayerTargetVanillaPrediction() {
        assertEquals(ActionResult.SUCCESS, CeremonialSwordCombatService.clientAttackResult(true, true));
        assertEquals(ActionResult.PASS, CeremonialSwordCombatService.clientAttackResult(true, false));
        assertEquals(ActionResult.PASS, CeremonialSwordCombatService.clientAttackResult(false, true));
    }

    @Test
    void ceremonialSwordDashStartGateIsItemOnlyAndNotRoundBound() {
        assertTrue(CeremonialSwordItem.shouldStartDash(true, true, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(false, true, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, false, false, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, true, true, false));
        assertFalse(CeremonialSwordItem.shouldStartDash(true, true, false, true));
    }

    @Test
    void ceremonialSwordDashKeepsMovingOutsideWatheRound() {
        assertTrue(CeremonialSwordDashService.shouldKeepDashActive(true, true, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(false, true, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(true, false, false));
        assertFalse(CeremonialSwordDashService.shouldKeepDashActive(true, true, true));
    }

    @Test
    void ceremonialSwordUsesDedicatedDeathReason() {
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getNamespace().equals(SparkWitch.MOD_ID));
        assertTrue(SparkWitchDeathReasons.CEREMONIAL_BLADE.getPath().equals("ceremonial_blade"));
    }

    @Test
    void deathRayUsesDedicatedDeathReason() {
        assertEquals(SparkWitch.MOD_ID, SparkWitchDeathReasons.PIERCED_BY_RAY.getNamespace());
        assertEquals("pierced_by_ray", SparkWitchDeathReasons.PIERCED_BY_RAY.getPath());
    }
}
