package dev.caecorthus.sparkwitch.roles.killer.ninja;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaItemResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/sparkwitch");

    @Test
    void shipsTheUpstreamWeaponModelsAndTexturesUnderSparkWitchIds() throws IOException {
        assertItemResource("ninja_knife", 32);
        assertItemResource("ninja_shuriken", 16);
    }

    @Test
    void localizesWeaponsTooltipsAndReplayForBothDeathReasons() throws IOException {
        for (String locale : new String[]{"en_us.json", "zh_cn.json"}) {
            String lang = Files.readString(ASSETS.resolve("lang/" + locale));
            assertTrue(lang.contains("\"item.sparkwitch.ninja_knife\""));
            assertTrue(lang.contains("\"item.sparkwitch.ninja_knife.desc\""));
            assertTrue(lang.contains("\"item.sparkwitch.ninja_shuriken\""));
            assertTrue(lang.contains("\"item.sparkwitch.ninja_shuriken.desc\""));
            assertTrue(lang.contains("\"death_reason.sparkwitch.ninja_knife_kill\""));
            assertTrue(lang.contains("\"replay.death.sparkwitch.ninja_knife_kill.killed\""));
            assertTrue(lang.contains("\"replay.death.sparkwitch.ninja_knife_kill.died\""));
            assertTrue(lang.contains("\"death_reason.sparkwitch.ninja_shuriken_kill\""));
            assertTrue(lang.contains("\"replay.death.sparkwitch.ninja_shuriken_kill.killed\""));
            assertTrue(lang.contains("\"replay.death.sparkwitch.ninja_shuriken_kill.died\""));
        }
    }

    @Test
    void documentsKunaiLeftClickKnockbackInBothTooltips() throws IOException {
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));

        assertTrue(english.contains(
                "\"item.sparkwitch.ninja_knife.desc\": \"Right-click to silently assassinate a living player within 4 blocks; left-click to knock players back.\""
        ));
        assertTrue(chinese.contains(
                "\"item.sparkwitch.ninja_knife.desc\": \"右键无声刺杀4格内的一名存活玩家；左键击退玩家。\""
        ));
    }

    @Test
    void shipsPinnedStarRailExpressLicenseAndProvenanceInTheJar() throws IOException {
        Path copiedLicense = Path.of("licenses/StarRailExpress-GPL-3.0-only.txt");
        String notice = Files.readString(Path.of("THIRD_PARTY_NOTICES.md"));
        String build = Files.readString(Path.of("build.gradle"));

        assertEquals(
                "0a4c381b1145e2fd01b35126d2213fcbcd05315d162c7fcf5bd0298cca188ebe",
                sha256(copiedLicense)
        );
        assertTrue(notice.contains("https://github.com/catmoon-train/StarRailExpress"));
        assertTrue(notice.contains("220d03ede335fc7971fcffbc302bc68bb91b0209"));
        assertTrue(notice.contains("GPL-3.0-only"));
        assertTrue(build.contains("licenses/StarRailExpress-GPL-3.0-only.txt"));
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static void assertItemResource(String itemId, int expectedSize) throws IOException {
        Path modelPath = ASSETS.resolve("models/item/" + itemId + ".json");
        Path texturePath = ASSETS.resolve("textures/item/" + itemId + ".png");
        String model = Files.readString(modelPath);
        BufferedImage texture = ImageIO.read(texturePath.toFile());

        assertTrue(model.contains("sparkwitch:item/" + itemId));
        assertNotNull(texture);
        assertEquals(expectedSize, texture.getWidth());
        assertEquals(expectedSize, texture.getHeight());
        assertTrue(texture.getColorModel().hasAlpha());
    }
}
