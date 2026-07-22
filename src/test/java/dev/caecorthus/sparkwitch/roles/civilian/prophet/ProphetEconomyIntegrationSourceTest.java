package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetEconomyIntegrationSourceTest {
    @Test
    void serverChecksAndDeductsBeforeStartingTheWindow() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetSkillService.java"
        ));
        int balanceCheck = source.indexOf("shop.getBalance() < ProphetRules.COIN_COST");
        int deduction = source.indexOf("shop.setBalance(shop.getBalance() - ProphetRules.COIN_COST)");
        int activation = source.indexOf("component.beginDeathOmenWindow");

        assertTrue(balanceCheck >= 0);
        assertTrue(deduction > balanceCheck);
        assertTrue(activation > deduction);
        assertTrue(source.contains("WitchSkillUseResult.fail"));
    }

    @Test
    void hudAndFailureMessagesAreLocalizedExactly() throws Exception {
        JsonObject zh = language("zh_cn");
        JsonObject en = language("en_us");
        assertEquals("花费 %s 金币使用技能", zh.get("hud.sparkwitch.skill.death_omen.coin_cost").getAsString());
        assertEquals("Spend %s coins to use the skill", en.get("hud.sparkwitch.skill.death_omen.coin_cost").getAsString());
        assertTrue(zh.has("message.sparkwitch.skill.death_omen.not_enough_money"));
        assertTrue(en.has("message.sparkwitch.skill.death_omen.not_enough_money"));
    }

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/sparkwitch/lang/" + locale + ".json"
        ))).getAsJsonObject();
    }
}
