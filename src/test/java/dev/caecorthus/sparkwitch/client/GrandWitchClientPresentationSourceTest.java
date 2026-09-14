package dev.caecorthus.sparkwitch.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Source contracts cover the split client source set without bootstrapping Minecraft. */
class GrandWitchClientPresentationSourceTest {
    private static final Path CLIENT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void recruitmentUsesOnlyRoleOwnedSecondaryDispatchAndServerPacket() throws IOException {
        String module = source("grandwitch/GrandWitchClientModule.java");
        assertTrue(module.contains("SecondaryAbilityRegistry.register(SparkWitchRoles.GRAND_WITCH_ID"));
        assertTrue(module.contains("hasUnlockedGrandWitchCeremonialSword()"));
        assertTrue(module.contains("new GrandWitchRecruitC2SPacket(target == null ? null : target.getUuid())"));
        assertTrue(module.contains("if (registered)"));
        assertFalse(module.contains("WitchSkillRegistry.register"));
        assertFalse(module.contains("setCooldown"));
        assertFalse(module.contains("setMana"));
        assertTrue(source("ability/SecondaryAbilityController.java").contains("GLFW.GLFW_KEY_N"));
        String targeting = source("grandwitch/GrandWitchClientTargeting.java");
        assertTrue(targeting.contains("REACH = 8.0D"));
        assertTrue(targeting.contains("GameFunctions.isPlayerPlayingAndAlive(target)"));
        assertTrue(targeting.contains("user.canSee(target)"));
    }

    @Test
    void hudReadsIndependentTimersAndCumulativeOpeningPopulationQuota() throws IOException {
        String presentation = source("grandwitch/GrandWitchClientPresentation.java");
        assertTrue(presentation.contains("SparkWitch.id(\"witch_factor\")"));
        assertTrue(presentation.contains("getSwordKillCooldownTicks()"));
        assertTrue(presentation.contains("cooldown.sparkwitch$getEndTick() - manager.sparkwitch$getTick()"));
        assertFalse(presentation.contains("getCooldownProgress("));
        assertTrue(presentation.contains("(runtime.getRoundParticipants() - 18) / 6"));
        assertTrue(presentation.contains("capacity - runtime.getRecruitmentCount()"));
        assertTrue(presentation.contains("SecondaryAbilityController.secondaryKeyText()"));
        assertTrue(presentation.contains("SparkWitchClient.abilityKeyText()"));
        assertTrue(presentation.contains("hud.sparkwitch.grand_witch.sword.kill"));
        assertTrue(presentation.contains("hud.sparkwitch.grand_witch.sword.dash"));
        String hud = source("hud/WitchSkillHudRenderer.java");
        assertTrue(hud.indexOf("GrandWitchClientPresentation.renderHud") < hud.indexOf("component.getActiveSkillId()"));
    }

    @Test
    void panelPreservesRoleAndOwnSkillGateBeforeGrandWitchRows() throws IOException {
        String panel = source("mixin/WitchSkillInventoryScreenMixin.java");
        assertTrue(panel.indexOf("WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, skillId)")
                < panel.indexOf("sparkwitch$renderGrandWitchSkills(context"));
        assertTrue(panel.contains("skill.sparkwitch.recruit_accomplice.name"));
        assertTrue(panel.contains("GrandWitchClientPresentation.swordStates(player)"));
        assertTrue(panel.contains("wrapLines(line, width)"));
    }

    @Test
    void watheOverridesAreLimitedToConfirmedGrandWitchMainHandSword() throws IOException {
        for (String name : List.of("GrandWitchSwordCooldownMixin", "GrandWitchSwordCrosshairMixin")) {
            String mixin = source("mixin/" + name + ".java");
            assertTrue(mixin.contains("SparkWitchServerConnection.isConfirmedServer()"));
            assertTrue(mixin.contains("player.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())"));
            assertTrue(mixin.contains("GrandWitchClientPresentation.isGrandWitch(player)"));
        }
        String cooldown = source("mixin/GrandWitchSwordCooldownMixin.java");
        assertTrue(cooldown.contains("@Mixin(CooldownRenderer.class)"));
        assertTrue(cooldown.contains("ci.cancel()"));
        String crosshair = source("mixin/GrandWitchSwordCrosshairMixin.java");
        assertTrue(crosshair.contains("getAttackCooldownProgress("));
        assertFalse(crosshair.contains("getCooldownProgress("));
        assertTrue(crosshair.contains("getPerspective().isFirstPerson()"));
        assertTrue(crosshair.contains("GameFunctions.isPlayerPlayingAndAlive(player)"));
    }

    @Test
    void bothLanguagesResolveAllGrandWitchUiKeysAndRemoveTemporaryUltimate() throws IOException {
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/sparkwitch/lang/" + language + ".json"))).getAsJsonObject();
            for (String file : List.of("grandwitch/GrandWitchClientModule.java",
                    "grandwitch/GrandWitchClientPresentation.java", "mixin/WitchSkillInventoryScreenMixin.java")) {
                var keys = Pattern.compile("\"((?:hud|skill|item)\\.sparkwitch\\.(?:grand_witch|witch_factor|recruit_accomplice|ceremonial_sword)[^\"]*)\"")
                        .matcher(source(file));
                while (keys.find()) {
                    assertTrue(translations.has(keys.group(1)), language + ": " + keys.group(1));
                }
            }
            String sword = translations.get("skill.sparkwitch.ceremonial_sword.description").getAsString();
            assertFalse(sword.contains("150"));
            assertFalse(sword.contains("15 秒"));
            assertFalse(sword.contains("15 seconds"));
            assertTrue(sword.contains("40%"));
            assertTrue(sword.contains("30"));
        }
    }

    private static String source(String relative) throws IOException {
        return Files.readString(CLIENT.resolve(relative));
    }
}
