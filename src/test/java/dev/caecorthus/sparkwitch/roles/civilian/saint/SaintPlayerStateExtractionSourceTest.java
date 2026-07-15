package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintPlayerStateExtractionSourceTest {
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path NBT_CODEC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java");
    private static final Path SYNC_CODEC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java");

    @Test
    void keepsSaintStateOutOfTheSharedComponentBody() throws IOException {
        String component = Files.readString(COMPONENT);

        assertTrue(component.contains("private final SaintPlayerState saintState = new SaintPlayerState()"));
        assertTrue(component.contains("public SaintPlayerState getSaintState()"));
        assertFalse(component.contains("int saintHellfireCooldownTicks;"));
        assertFalse(component.contains("boolean saintKarmaMarked;"));
    }

    @Test
    void delegatesSaintPersistenceWithoutChangingItsPacketPosition() throws IOException {
        String nbtCodec = Files.readString(NBT_CODEC);
        String syncCodec = Files.readString(SYNC_CODEC);

        assertTrue(nbtCodec.contains("component.getSaintState().writeNbt(tag)"));
        assertTrue(nbtCodec.contains("component.getSaintState().readNbt(tag)"));

        int deathRayWrite = syncCodec.indexOf("component.deathRayCharges : 0");
        int saintWrite = syncCodec.indexOf("component.getSaintState().writeSync(buf, ownerVisible)", deathRayWrite);
        int deathRayRead = syncCodec.indexOf("component.deathRayCharges =", saintWrite);
        int saintRead = syncCodec.indexOf("component.getSaintState().readSync(buf)", deathRayRead);
        assertTrue(deathRayWrite >= 0 && saintWrite > deathRayWrite);
        assertTrue(deathRayRead > saintWrite && saintRead > deathRayRead);
    }
}
