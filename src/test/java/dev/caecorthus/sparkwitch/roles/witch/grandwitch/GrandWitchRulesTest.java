package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordCombatService;
import net.minecraft.util.ActionResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchRulesTest {
    @Test
    void ceremonialSwordProgressUsesTwoTaskUnlockBoundary() {
        assertEquals(2, GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS);
        assertEquals(0, GrandWitchRules.clampCeremonialSwordTaskProgress(-1));
        assertEquals(2, GrandWitchRules.clampCeremonialSwordTaskProgress(99));
        assertFalse(GrandWitchRules.isCeremonialSwordUnlocked(1));
        assertTrue(GrandWitchRules.isCeremonialSwordUnlocked(2));
    }

    @Test
    void permanentSwordIsGrantedOnlyOnSecondTaskTransition() {
        assertFalse(GrandWitchRules.shouldGrantCeremonialSword(0, 1));
        assertTrue(GrandWitchRules.shouldGrantCeremonialSword(1, 2));
        assertFalse(GrandWitchRules.shouldGrantCeremonialSword(2, 2));
        assertFalse(GrandWitchRules.shouldGrantCeremonialSword(2, 3));
        assertFalse(GrandWitchRules.shouldGrantCeremonialSword(3, 4));
    }

    @Test
    void swordCooldownAndReentryAreIndependentOfVanillaChargeAndDash() {
        assertEquals(600, GrandWitchRules.CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS);
        assertEquals(2.0, GrandWitchRules.CEREMONIAL_SWORD_ATTACK_SPEED);
        assertEquals(0.4, GrandWitchRules.CEREMONIAL_SWORD_MOVEMENT_SPEED_BONUS);
        assertTrue(GrandWitchRules.canAttemptCeremonialSwordKill(0, false));
        assertFalse(GrandWitchRules.canAttemptCeremonialSwordKill(1, false));
        assertFalse(GrandWitchRules.canAttemptCeremonialSwordKill(600, false));
        assertFalse(GrandWitchRules.canAttemptCeremonialSwordKill(0, true));
        assertFalse(GrandWitchRules.canAttemptCeremonialSwordKill(600, true));
    }

    @Test
    void leftClickRequiresFullChargeAndNeverFallsThroughToVanillaDamage() {
        assertEquals(new CeremonialSwordCombatService.AttackDecision(true, true, true),
                CeremonialSwordCombatService.decideAttack(true, true));
        assertEquals(new CeremonialSwordCombatService.AttackDecision(true, false, false),
                CeremonialSwordCombatService.decideAttack(true, false));
        assertEquals(new CeremonialSwordCombatService.AttackDecision(true, true, false),
                CeremonialSwordCombatService.decideAttack(false, true));
        assertEquals(new CeremonialSwordCombatService.AttackDecision(true, false, false),
                CeremonialSwordCombatService.decideAttack(false, false));
        assertEquals(ActionResult.SUCCESS, CeremonialSwordCombatService.clientAttackResult(true, true));
        assertEquals(ActionResult.SUCCESS, CeremonialSwordCombatService.clientAttackResult(true, false));
        assertEquals(ActionResult.PASS, CeremonialSwordCombatService.clientAttackResult(false, true));
    }

    @Test
    void contactGatePrecedesProtectionAndCooldownStartsOnlyAfterDeath() throws Exception {
        String combat = source("item/ceremonialsword/CeremonialSwordCombatService.java");
        String kill = combat.substring(combat.indexOf("public static void killWithCeremonialSword"));
        assertTrue(kill.indexOf("canAttemptCeremonialSwordKill(") < kill.indexOf("shouldCancelMeleeAttack("));
        assertTrue(kill.indexOf("setSwordStrikeInProgress(true)") < kill.indexOf("shouldCancelMeleeAttack("));
        assertTrue(combat.contains("KillPlayer.AFTER.register("));
        assertTrue(combat.contains("SparkWitchDeathReasons.CEREMONIAL_BLADE.equals(reason)"));
        assertTrue(combat.contains("completeSwordKill(killer, victim)"));
        assertFalse(kill.contains("target.isSpectator() || !target.isAlive()"));
        assertTrue(kill.contains("boundVendetta && !VendettaInteractionService.isActiveVendetta(target)"));
        assertTrue(kill.contains("finally {\n            runtime.setSwordStrikeInProgress(false);"));
        assertTrue(combat.contains("getAttackCooldownProgress(0.0f) >= 1.0f"));
        assertTrue(combat.contains("serverAttacker.resetLastAttackedTicks()"));
        assertFalse(combat.contains("getItemCooldownManager()"));
        String dash = source("item/ceremonialsword/CeremonialSwordDashService.java");
        assertTrue(dash.contains("CeremonialSwordCombatService.killWithCeremonialSword(player, target)"));
        assertFalse(dash.contains("getSwordKillCooldownTicks"));
    }

    @Test
    void permanentSwordUsesMainHandAttributesAndKeepsSeparateFiveSecondDash() throws Exception {
        String item = source("item/ceremonialsword/CeremonialSwordItem.java");
        assertTrue(item.contains("DASH_COOLDOWN_TICKS = 100"));
        assertTrue(item.contains("GrandWitchRules.CEREMONIAL_SWORD_ATTACK_SPEED - 4.0"));
        assertTrue(item.contains("EntityAttributes.GENERIC_MOVEMENT_SPEED"));
        assertTrue(item.contains("EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL"));
        assertEquals(2, item.split("AttributeModifierSlot.MAINHAND", -1).length - 1);
        assertFalse(item.contains("getSwordKillCooldownTicks"));
        String service = source("roles/witch/grandwitch/GrandWitchActiveSkillService.java");
        assertAll(
                () -> assertFalse(service.contains("spendMana(")),
                () -> assertFalse(service.contains("beginCeremonialSwordWindow(")),
                () -> assertFalse(service.contains("startGrandWitchCeremonialSwordBgm(")),
                () -> assertFalse(service.contains("addStatusEffect(")),
                () -> assertTrue(service.contains("GrandWitchRules.shouldGrantCeremonialSword(previousTasks,")),
                () -> assertTrue(service.contains("inventory.setStack(knifeSlot, sword)")),
                () -> assertTrue(service.contains("!inventory.insertStack(sword)")),
                () -> assertTrue(service.contains("player.dropItem(sword, false)"))
        );
    }

    @Test
    void languageFilesNoLongerAdvertiseTemporaryManaSpell() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));
        assertFalse(english.contains("Spend 150 mana to turn one knife into a Ceremonial Sword for 15 seconds"));
        assertFalse(chinese.contains("消耗 150 点魔力值，将一把匕首临时替换为 15 秒仪礼剑"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch").resolve(path));
    }

    @Test
    void spellEntryIdsAndManaCostsRemainStable() {
        assertEquals(80, spell("sparkwitch_obscure").manaCost());
        assertEquals(80, spell("sparkwitch_blindness").manaCost());
        assertEquals(50, spell("sparkwitch_fear").manaCost());
        assertEquals(60, spell("sparkwitch_heaviness").manaCost());
        assertEquals("shop.sparkwitch.obscure",
                GrandWitchRules.GrandWitchSpell.OBSCURE.translationKey());
        assertEquals("shop.sparkwitch.obscure.description",
                GrandWitchRules.GrandWitchSpell.OBSCURE.descriptionTranslationKey());
        assertNull(GrandWitchRules.GrandWitchSpell.fromEntryId("sparkwitch_unknown"));
    }

    private static GrandWitchRules.GrandWitchSpell spell(String entryId) {
        return GrandWitchRules.GrandWitchSpell.fromEntryId(entryId);
    }
}
