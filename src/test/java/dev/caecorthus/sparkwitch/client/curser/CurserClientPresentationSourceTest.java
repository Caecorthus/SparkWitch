package dev.caecorthus.sparkwitch.client.curser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurserClientPresentationSourceTest {
    @Test
    void confusionIsPrivateAndDoesNotMutateWathePsychoState() throws Exception {
        String component = Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/witch/curser/CurserPlayerComponent.java"));
        String skin = Files.readString(Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/CurserConfusionSkinMixin.java"));
        String instinct = Files.readString(Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/CurserInstinctGateMixin.java"));
        assertTrue(component.contains("return recipient == player"));
        assertTrue(component.contains("ConfusionTicks"));
        assertTrue(skin.contains("WATHE_PSYCHO_TEXTURE"));
        assertTrue(skin.contains("target == MinecraftClient.getInstance().player"));
        assertTrue(!skin.contains("PlayerPsychoComponent"));
        assertTrue(instinct.contains("isInstinctEnabled"));
        assertTrue(instinct.contains("isLocallyConfused"));
    }
}
