package dev.caecorthus.sparkwitch.voice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurVoicePluginContractTest {
    @Test
    void filtersAllOutboundSoundPacketTypesAfterWatheRelay() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/voice/SparkWitchVoiceChatPlugin.java"));
        assertTrue(source.contains("EntitySoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(source.contains("LocationalSoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(source.contains("StaticSoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(source.contains("SaboteurVoiceRules.shouldBlockPacket(event)"));
    }

    @Test
    void preservesGuardianWraithMicrophoneGate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/voice/SparkWitchVoiceChatPlugin.java"));
        assertTrue(source.contains("GuardianAngelRules.shouldBlockWraithMicrophone"));
    }
}
