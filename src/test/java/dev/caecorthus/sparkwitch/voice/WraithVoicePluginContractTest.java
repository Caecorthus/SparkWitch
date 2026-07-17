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
        assertFalse(source.contains("EntitySoundPacketEvent"));
        assertFalse(source.contains("LocationalSoundPacketEvent"));
        assertFalse(source.contains("Depression"));
        assertFalse(source.contains("getReceiverConnection"));
    }
}
