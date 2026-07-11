package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

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
    void languageFilesDescribeTheExactTwoTaskCeremonialSwordUnlock() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));

        assertAll(
                () -> assertTrue(english.contains("\"skill.sparkwitch.ceremonial_sword.description\": \"Unlocked after 2 completed tasks. Spend 150 mana to turn one knife into a Ceremonial Sword for 15 seconds and gain Speed II.\"")),
                () -> assertTrue(chinese.contains("\"skill.sparkwitch.ceremonial_sword.description\": \"完成 2 个任务后解锁。消耗 150 点魔力值，将一把匕首临时替换为 15 秒仪礼剑，并获得速度 II。\""))
        );
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
