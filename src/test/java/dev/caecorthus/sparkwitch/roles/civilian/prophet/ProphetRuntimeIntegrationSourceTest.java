package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetRuntimeIntegrationSourceTest {
    private static final Path RUNTIME = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/prophet/ProphetRuntime.java");
    private static final Path SKILLS = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/skill/SparkWitchBuiltInSkills.java");
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path EVENTS = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java");
    private static final Path LANG = Path.of("src/main/resources/assets/sparkwitch/lang");

    @Test
    void collectsRealBodiesAtTheServerSpawnBoundary() throws IOException {
        String runtime = Files.readString(RUNTIME);
        assertTrue(runtime.contains("ServerEntityEvents.ENTITY_LOAD.register"));
        assertTrue(runtime.contains("entity instanceof PlayerBodyEntity body"));
        assertTrue(runtime.contains("body.getDeathGameTime()"));
        assertTrue(runtime.contains("gameComponent.isPlayerDead(body.getPlayerUuid())"));
        assertTrue(runtime.contains("recordDeathOmenBody(body.getUuid())"));
        assertFalse(runtime.contains("KillPlayer.BEFORE.register"));

        int entityLoad = runtime.indexOf("private static void onEntityLoad");
        int activeAtLoad = runtime.indexOf("component.isDeathOmenActive()", entityLoad);
        int recordBody = runtime.indexOf("recordDeathOmenBody(body.getUuid())", activeAtLoad);
        assertTrue(entityLoad >= 0 && activeAtLoad > entityLoad && recordBody > activeAtLoad);
    }

    @Test
    void cancelsADeadOrReassignedProphetWithoutPostCooldown() throws IOException {
        String runtime = Files.readString(RUNTIME);
        assertTrue(runtime.contains("KillPlayer.AFTER.register"));
        assertTrue(runtime.contains("component.isDeathOmenActive()"));
        assertTrue(runtime.contains("cancelDeathOmenWindow()"));
        assertTrue(Files.readString(EVENTS).contains("ProphetRuntime.assignForRole"));
    }

    @Test
    void registersTheRoleOwnedSkillAndTicksAfterSharedCooldown() throws IOException {
        String skills = Files.readString(SKILLS);
        assertTrue(skills.contains("ProphetRules.DEATH_OMEN_ID"));
        assertTrue(skills.contains("ProphetRules.INITIAL_COOLDOWN_TICKS"));
        assertTrue(skills.contains("ProphetRules.POST_COOLDOWN_TICKS"));
        assertTrue(skills.contains("ProphetSkillService::use"));

        String component = Files.readString(COMPONENT);
        int cooldown = component.indexOf("tickCooldown()");
        int prophet = component.indexOf("ProphetRuntime.tick(serverPlayer, this)", cooldown);
        int mana = component.indexOf("WitchManaService.tickRegeneration", prophet);
        assertTrue(cooldown >= 0 && prophet > cooldown && mana > prophet);

        String events = Files.readString(EVENTS);
        assertTrue(events.contains("ProphetRuntime.register()"));
    }

    @Test
    void localizesTheApprovedSkillCopy() throws IOException {
        JsonObject chinese = parse("zh_cn");
        JsonObject english = parse("en_us");
        assertEquals("死亡预兆", chinese.get("skill.sparkwitch.death_omen.name").getAsString());
        assertEquals("开局冷却 60 秒。开启后持续 20 秒，并在 128 格内隔墙红色高亮期间新产生的尸体；完整结束后冷却 90 秒。",
                chinese.get("skill.sparkwitch.death_omen.description").getAsString());
        assertEquals("死亡预兆已经降临。",
                chinese.get("message.sparkwitch.skill.death_omen.activated").getAsString());
        assertEquals("Death Omen", english.get("skill.sparkwitch.death_omen.name").getAsString());
        assertEquals("After a 60-second initial cooldown, reveal newly created bodies through walls within 128 blocks for 20 seconds. A full omen starts a 90-second cooldown.",
                english.get("skill.sparkwitch.death_omen.description").getAsString());
        assertEquals("Death Omen has begun.",
                english.get("message.sparkwitch.skill.death_omen.activated").getAsString());
    }

    private static JsonObject parse(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json")))
                .getAsJsonObject();
    }
}
