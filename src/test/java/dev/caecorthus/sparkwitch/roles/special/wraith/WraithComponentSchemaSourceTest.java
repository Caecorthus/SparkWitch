package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithComponentSchemaSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void canonicalPlayerSchemaKeepsExactIdKeysOrderAndPrivacy() throws IOException {
        String source = compact(read("component/WraithPlayerComponent.java"));

        assertTrue(source.contains("SparkWitch.id(\"wraith_player\")"));
        assertInOrder(source,
                "buf.writeBoolean(state.isActive())",
                "buf.writeBoolean(state.isRestricted())",
                "buf.writeVarInt(owner ? state.getCompletedTasks() : 0)",
                "buf.writeVarInt(owner && state.getAlignment() != null ? state.getAlignment().ordinal() : -1)",
                "buf.writeBoolean(owner && state.isPromotionPending())");
        assertInOrder(source,
                "buf.readBoolean()",
                "buf.readBoolean()",
                "buf.readVarInt()",
                "buf.readVarInt()",
                "buf.readBoolean()");
        assertTrue(source.contains("tag.putBoolean(\"WraithActive\", true)"));
        assertTrue(source.contains("tag.putBoolean(\"WraithRestricted\", state.isRestricted())"));
        assertTrue(source.contains("tag.putInt(\"WraithCompletedTasks\", state.getCompletedTasks())"));
        assertTrue(source.contains("tag.putString(\"WraithAlignment\", state.getAlignment().name())"));
        assertTrue(source.contains("tag.putBoolean(\"WraithPromotionPending\", true)"));
        assertFalse(source.contains("sparktraits:wraith_player"));
    }

    @Test
    void canonicalRoundAndLegacyReadersUseExactIdsAndCanonicalOnlyWrites() throws IOException {
        String canonical = read("component/WraithRoundComponent.java");
        String settingsCodec = read("roles/special/wraith/WraithSettingsNbtCodec.java");
        String legacyPlayer = compact(read("component/LegacyWraithPlayerComponent.java"));
        String legacyRound = compact(read("component/LegacyWraithRoundComponent.java"));
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));

        assertTrue(canonical.contains("SparkWitch.id(\"wraith_round\")"));
        assertTrue(canonical.contains("\"StartingPlayerCount\""));
        assertTrue(settingsCodec.contains("\"Chance\""));
        assertTrue(settingsCodec.contains("\"Minimum\""));
        assertTrue(settingsCodec.contains("\"Dividend\""));
        assertTrue(canonical.contains("\"ConsumedPlayers\""));
        assertFalse(canonical.contains("sparktraits:wraith_round"));
        assertTrue(legacyPlayer.contains("Identifier.of(\"sparktraits\", \"wraith_player\")"));
        assertTrue(legacyRound.contains("Identifier.of(\"sparktraits\", \"wraith_round\")"));
        assertTrue(legacyPlayer.contains("public void writeToNbt("));
        assertTrue(legacyRound.contains("public void writeToNbt("));
        assertFalse(legacyPlayer.contains("tag.put"));
        assertFalse(legacyRound.contains("tag.put"));
        assertTrue(metadata.contains("\"sparkwitch:wraith_player\""));
        assertTrue(metadata.contains("\"sparkwitch:wraith_round\""));
        assertTrue(metadata.contains("\"sparktraits:wraith_player\""));
        assertTrue(metadata.contains("\"sparktraits:wraith_round\""));
    }

    @Test
    void apiAndWatheMixinsAreRegisteredAtReadBoundaries() throws IOException {
        String api = compact(read("api/SparkWitchApi.java"));
        String gameWorldMixin = compact(read("mixin/WraithGameWorldNbtMixin.java"));
        String roleHistoryMixin = compact(read("mixin/WraithRoleHistoryNbtMixin.java"));
        String mixins = Files.readString(Path.of("src/main/resources/sparkwitch.mixins.json"));

        assertTrue(api.contains("player != null"));
        assertTrue(api.contains("WraithPlayerComponent.KEY.maybeGet(player)"));
        assertTrue(api.contains("isKillerAlignedWraith(wraith.isActive(), wraith.getAlignment())"));
        assertTrue(api.contains("alignment == WraithState.Alignment.KILLER"));
        assertFalse(api.contains("alignment == null"));
        assertTrue(gameWorldMixin.contains("method = \"readFromNbt\""));
        assertTrue(gameWorldMixin.contains("at = @At(\"HEAD\")"));
        assertTrue(roleHistoryMixin.contains("method = \"readFromNbt\""));
        assertTrue(roleHistoryMixin.contains("at = @At(\"HEAD\")"));
        assertTrue(mixins.contains("\"WraithGameWorldNbtMixin\""));
        assertTrue(mixins.contains("\"WraithRoleHistoryNbtMixin\""));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }

    private static void assertInOrder(String source, String... fragments) {
        int cursor = -1;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, cursor + 1);
            assertTrue(next > cursor, () -> "Missing or out-of-order fragment: " + fragment);
            cursor = next;
        }
    }
}
