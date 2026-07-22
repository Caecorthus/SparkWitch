package dev.caecorthus.sparkwitch.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostSettingsCommandSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void commandsUseBoundedArgumentsPermissionsAndNextRoundSettings() throws IOException {
        String commands = Files.readString(MAIN.resolve("command/GhostSettingsCommand.java"));
        String wathe = Files.readString(MAIN.resolve("command/WatheGhostDividendCommand.java"));

        assertTrue(commands.contains("literal(\"sparkwitch:ghostChance\")"));
        assertTrue(commands.contains("IntegerArgumentType.integer(0, 100)"));
        assertTrue(commands.contains("literal(\"sparkwitch:ghostMinRequirement\")"));
        assertTrue(commands.contains("IntegerArgumentType.integer(0)"));
        assertTrue(wathe.contains("literal(\"wathe:gameSettings\")"));
        assertTrue(wathe.contains("literal(\"roleDividend\")"));
        assertTrue(wathe.contains("literal(\"ghost\")"));
        assertTrue(wathe.contains("IntegerArgumentType.integer(1)"));
        assertTrue(wathe.contains("WATHE_PERMISSION = \"wathe.command.gamesettings\""));
    }

    @Test
    void persistenceDoesNotChangeWorldSyncPacketSchema() throws IOException {
        String world = Files.readString(MAIN.resolve("component/WitchWorldComponent.java"));
        String round = Files.readString(MAIN.resolve("component/WraithRoundComponent.java"));
        String codec = Files.readString(MAIN.resolve("roles/special/wraith/WraithSettingsNbtCodec.java"));

        assertTrue(codec.contains("tag.putInt(WORLD_CHANCE_KEY"));
        assertTrue(codec.contains("tag.putInt(WORLD_MINIMUM_KEY"));
        assertTrue(codec.contains("tag.putInt(WORLD_DIVIDEND_KEY"));
        assertTrue(!world.substring(world.indexOf("writeSyncPacket"), world.indexOf("applySyncPacket"))
                .contains("wraithSettings"));
        assertTrue(round.contains("WraithSettingsNbtCodec.readRound"));
        assertTrue(codec.contains("WraithSettings.DEFAULT_CHANCE"));
        assertTrue(codec.contains("WraithSettings.DEFAULT_MINIMUM"));
        assertTrue(codec.contains("WraithSettings.DEFAULT_DIVIDEND"));
    }
}
