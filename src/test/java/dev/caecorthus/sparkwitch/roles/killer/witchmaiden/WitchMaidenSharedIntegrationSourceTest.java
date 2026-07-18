package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchMaidenSharedIntegrationSourceTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void wiresRoleOwnedServerModulesWithoutChangingTheSharedPlayerSchema() throws IOException {
        String initializer = main("SparkWitch.java");
        String events = main("impl/SparkWitchEvents.java");
        String skills = main("skill/SparkWitchBuiltInSkills.java");
        String rules = main("roles/killer/witchmaiden/WitchMaidenRules.java");
        String poisonApple = main("roles/killer/witchmaiden/PoisonApplePlateService.java");

        assertTrue(initializer.contains("FocusedFootstepsEffects.register();"));
        assertTrue(events.contains("WitchMaidenFeatureService.register();"));
        assertTrue(events.contains("FocusedFootstepsRuntime.register();"));
        assertTrue(events.contains("WitchMaidenShopService.register();"));
        assertTrue(events.contains("PoisonApplePlateService.clearLoadedPlates();"));
        assertTrue(skills.contains("FocusedFootstepsSkillService::use"));
        assertTrue(skills.contains("FocusedFootstepsRules.INITIAL_COOLDOWN_TICKS"));
        assertTrue(skills.contains("WitchMaidenRules.isWitchMaiden(context.role())"));
        assertTrue(rules.contains("NoellesRoleIds.VOODOO_CURSE_DEATH_REASON"));
        assertTrue(poisonApple.contains("NoellesToxicologistBridge"));
        assertFalse(poisonApple.contains("org.agmas.noellesroles"));

        String component = main("component/WitchPlayerComponent.java");
        assertFalse(component.contains("focusedFootstepsTarget"));
        assertFalse(component.contains("poisonApple"));
    }

    @Test
    void inventoryAvatarDispatchSkipsOnlyWitchMaidenRoleKeyAndKeepsClientLifecycle() throws IOException {
        String client = client("client/SparkWitchClient.java");
        String presentation = main("skill/WitchSkillPresentationRules.java");
        String hud = client("client/hud/WitchSkillHudRenderer.java");
        String targetWidget = client("client/witchmaiden/WitchMaidenTargetWidget.java");

        assertTrue(client.contains("WitchMaidenClientModule.register();"));
        assertTrue(client.contains("WitchMaidenClientModule.clear();"));
        assertTrue(client.contains("!WitchMaidenRules.isWitchMaiden(role)"));
        assertTrue(client.contains("WitchPlayerComponent.KEY.get(client.player).hasSkill()"));
        assertTrue(client.contains("SaintRules.isSaint(role)"));
        assertFalse(presentation.contains("WitchMaidenRules"));
        assertTrue(hud.contains("if (FocusedFootstepsRules.SKILL_ID.equals(skillId)) {\n            return;"));
        assertFalse(hud.contains("WitchMaidenClientModule"));
        assertFalse(hud.contains("hud.sparkwitch.skill.focused_footsteps.ready"));
        assertTrue(targetWidget.contains("displayCooldownTicks"));
        assertTrue(targetWidget.contains("active = pageVisible\n                && !WitchMaidenClientModule.state().isRequestPending()"));
        assertTrue(targetWidget.contains("&& displayCooldownTicks() <= 0"));
        assertTrue(targetWidget.contains("context.drawTextWithShadow"));
        assertTrue(targetWidget.contains("0xFFFF0000"));
    }

    @Test
    void declaresEveryRoleOwnedMixinAdapter() throws IOException {
        JsonArray mainMixins = config("main/resources/sparkwitch.mixins.json").getAsJsonArray("mixins");
        JsonArray clientMixins = config("client/resources/sparkwitch.client.mixins.json").getAsJsonArray("client");

        assertContains(mainMixins, "witchmaiden.BeveragePlateBlockEntityPoisonAppleMixin");
        assertContains(mainMixins, "witchmaiden.BlockEntityPoisonAppleLifecycleMixin");
        assertContains(mainMixins, "witchmaiden.FoodPlatterBlockPoisonAppleMixin");
        assertContains(mainMixins, "witchmaiden.ItemStackPoisonAppleDrinkMixin");
        assertContains(clientMixins, "witchmaiden.BeveragePlateBlockEntityPoisonAppleParticleMixin");
        assertContains(clientMixins, "witchmaiden.FocusedFootstepsKeyboardInputMixin");
        assertContains(clientMixins, "witchmaiden.FocusedFootstepsOwnerSyncMixin");
        assertContains(clientMixins, "witchmaiden.WitchMaidenInventoryScreenMixin");
    }

    private static String main(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve("main/java/dev/caecorthus/sparkwitch").resolve(relativePath));
    }

    private static String client(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve("client/java/dev/caecorthus/sparkwitch").resolve(relativePath));
    }

    private static JsonObject config(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }

    private static void assertContains(JsonArray values, String expected) {
        assertTrue(values.asList().stream().anyMatch(value -> expected.equals(value.getAsString())));
    }
}
