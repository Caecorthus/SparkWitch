package dev.caecorthus.sparkwitch.voice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithVoicePluginContractTest {
    @Test
    void onlyOutgoingActiveWraithMicrophonePacketsAreCancelledAtMaximumPriority() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/voice/SparkWitchVoiceChatPlugin.java"))
                .replaceAll("\\s+", " ");
        assertTrue(source.contains("MicrophonePacketEvent.class, this::blockWraithSpeaker, Integer.MAX_VALUE"));
        assertTrue(source.contains("WraithStateService.isActive(speaker)"));
        assertTrue(source.contains("GuardianAngelRules.shouldBlockWraithMicrophone"));
        assertTrue(source.contains("EntitySoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(source.contains("LocationalSoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(source.contains("StaticSoundPacketEvent.class, this::blockSaboteurRecipient"));
        assertTrue(Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/voice/SaboteurVoiceRules.java"))
                .contains("getReceiverConnection"));
    }
}
