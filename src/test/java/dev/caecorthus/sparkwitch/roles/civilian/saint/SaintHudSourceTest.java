package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintHudSourceTest {
    private static final Path RENDERER = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/hud/SaintHudRenderer.java");
    private static final Path MIXIN = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/SaintHudMixin.java");

    @Test
    void stacksHellfireAndKarmaWhenAFormerKillerBecomesSaint() throws IOException {
        String renderer = readText(RENDERER);
        String mixin = readText(MIXIN);

        assertTrue(renderer.contains("List<Text> getHudLines"));
        assertTrue(renderer.contains("lines.add(hellfireLine"));
        assertTrue(renderer.contains("lines.add(Text.translatable(\n                    \"hud.sparkwitch.saint.karma.active\""));
        assertTrue(mixin.contains("List<Text> lines = SaintHudRenderer.getHudLines(player)"));
        assertTrue(mixin.contains("for (Text line : lines)"));
    }

    @Test
    void showsLocalizedCoinRequirementBeforeSaintCanInvokeHellfire() throws IOException {
        String renderer = readText(RENDERER);
        String english = readText(Path.of("src/main/resources/assets/sparkwitch/lang/en_us.json"));
        String chinese = readText(Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));

        assertTrue(renderer.contains("PlayerShopComponent.KEY.get(player).getBalance()"));
        int cooldown = renderer.indexOf("if (state.hellfireCooldownTicks() > 0)");
        int coinRequirement = renderer.indexOf("if (!SaintRules.hasHellfireCoinRequirement(balance))");
        assertTrue(cooldown >= 0 && coinRequirement > cooldown);
        assertTrue(renderer.contains("hud.sparkwitch.saint.hellfire.not_enough_money"));
        assertTrue(english.contains("\"hud.sparkwitch.saint.hellfire.not_enough_money\": \"Requires 175 coins\""));
        assertTrue(chinese.contains("\"hud.sparkwitch.saint.hellfire.not_enough_money\": \"需要 175 金币\""));
    }

    private static String readText(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }
}
