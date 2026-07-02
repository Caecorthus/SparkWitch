package dev.caecorthus.sparkwitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundResourcesTest {
    private static final Path SOUNDS_JSON = Path.of("src/main/resources/assets/sparkwitch/sounds.json");
    private static final Path PIG_CHASE_OGG = Path.of("src/main/resources/assets/sparkwitch/sounds/skill/pig_chase.ogg");
    private static final Path GRAND_WITCH_CEREMONIAL_SWORD_BGM_OGG =
            Path.of("src/main/resources/assets/sparkwitch/sounds/ambient/grand_witch_ceremonial_sword_bgm.ogg");

    @Test
    void pigChaseSoundIsDeclaredAndBundled() throws IOException {
        JsonObject sounds = JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();
        JsonObject pigChase = sounds.getAsJsonObject("skill.pig_chase");

        assertEquals("subtitles.sparkwitch.skill.pig_chase", pigChase.get("subtitle").getAsString());
        assertEquals("sparkwitch:skill/pig_chase", pigChase.getAsJsonArray("sounds").get(0).getAsString());
        assertFalse(pigChase.has("stream"));
        assertTrue(Files.isRegularFile(PIG_CHASE_OGG));
        assertTrue(Files.size(PIG_CHASE_OGG) > 0);
    }

    @Test
    void grandWitchCeremonialSwordBgmIsDeclaredStreamedAndBundled() throws IOException {
        JsonObject sounds = JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();
        JsonObject bgm = sounds.getAsJsonObject("ambient.grand_witch_ceremonial_sword_bgm");
        JsonObject sound = bgm.getAsJsonArray("sounds").get(0).getAsJsonObject();

        assertEquals("subtitles.sparkwitch.ambient.grand_witch_ceremonial_sword_bgm", bgm.get("subtitle").getAsString());
        assertEquals("sparkwitch:ambient/grand_witch_ceremonial_sword_bgm", sound.get("name").getAsString());
        assertTrue(sound.get("stream").getAsBoolean());
        assertTrue(Files.isRegularFile(GRAND_WITCH_CEREMONIAL_SWORD_BGM_OGG));
        assertTrue(Files.size(GRAND_WITCH_CEREMONIAL_SWORD_BGM_OGG) > 0);
    }
}
