package dev.caecorthus.sparkwitch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHookResourcesTest {
    private static final Path SPARK_WITCH_CLIENT =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/SparkWitchClient.java");
    private static final Path INSTINCT_SUPPRESSION_HOOK =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/hooks/WitchInstinctSuppressionClientHooks.java");
    private static final Path WATHE_FEAR_INSTINCT_MIXIN =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WatheClientFearInstinctMixin.java");
    private static final Path WITCH_SKILL_HUD_MIXIN =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WitchSkillHudMixin.java");
    private static final Path WITCH_MANA_HUD_MIXIN =
            Path.of("src/client/java/dev/caecorthus/sparkwitch/client/mixin/WitchManaHudMixin.java");

    @Test
    void clientRegistersWitchInstinctSuppressionHook() throws IOException {
        String source = Files.readString(SPARK_WITCH_CLIENT);

        assertTrue(source.contains("dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks"));
        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.register();"));
    }

    @Test
    void instinctSuppressionHookBeatsHighPriorityRoleHighlights() throws IOException {
        String source = Files.readString(INSTINCT_SUPPRESSION_HOOK);

        assertTrue(source.contains("GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 2"));
        assertTrue(source.contains("GetInstinctHighlight.EVENT.register"));
    }

    @Test
    void watheClientMixinSuppressesEarlyInstinctHighlightReturns() throws IOException {
        String source = Files.readString(WATHE_FEAR_INSTINCT_MIXIN);

        assertTrue(source.contains("priority = 1500"));
        assertTrue(source.contains("method = \"getInstinctHighlight\""));
        assertTrue(source.contains("WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()"));
    }

    @Test
    void watheClientMixinSuppressesSwallowedInstinctHighlightsBeforeEvents() throws IOException {
        String mixinSource = Files.readString(WATHE_FEAR_INSTINCT_MIXIN);
        String hookSource = Files.readString(INSTINCT_SUPPRESSION_HOOK);

        assertTrue(mixinSource.contains(
                "WitchInstinctSuppressionClientHooks.shouldSuppressSwallowedInstinctHighlight(target)"
        ));
        assertTrue(hookSource.contains("org.agmas.noellesroles.taotie.SwallowedPlayerComponent"));
        assertTrue(hookSource.contains("isPlayerSwallowed"));
    }

    @Test
    void clientGameplayHooksRequireConfirmedSparkWitchServer() throws IOException {
        String clientSource = Files.readString(SPARK_WITCH_CLIENT);
        String skillHudSource = Files.readString(WITCH_SKILL_HUD_MIXIN);
        String manaHudSource = Files.readString(WITCH_MANA_HUD_MIXIN);

        assertTrue(clientSource.contains("SparkWitchServerConnection.reset();"));
        assertTrue(clientSource.contains("ClientLoginConnectionEvents.INIT.register"));
        assertTrue(clientSource.contains("ClientLoginConnectionEvents.DISCONNECT.register"));
        assertTrue(clientSource.contains("ClientPlayConnectionEvents.INIT.register"));
        assertTrue(clientSource.contains("ClientPlayConnectionEvents.DISCONNECT.register"));
        assertTrue(clientSource.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(skillHudSource.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(manaHudSource.contains("SparkWitchServerConnection.isConfirmedServer()"));
    }

    @Test
    void clientNoLongerRegistersMigratedNoellesRoleEnhancementHooks() throws IOException {
        String clientSource = Files.readString(SPARK_WITCH_CLIENT);
        String skillHudSource = Files.readString(WITCH_SKILL_HUD_MIXIN);

        assertFalse(clientSource.contains("NoellesRoleEnhancementClientHooks"));
        assertFalse(clientSource.contains("CriminologistScreen"));
        assertFalse(skillHudSource.contains("CriminologistHudRenderer"));
    }
}
