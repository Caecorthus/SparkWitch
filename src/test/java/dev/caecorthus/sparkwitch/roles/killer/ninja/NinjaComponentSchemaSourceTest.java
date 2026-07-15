package dev.caecorthus.sparkwitch.roles.killer.ninja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaComponentSchemaSourceTest {
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path SYNC_CODEC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java");
    private static final Path NBT_CODEC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java");

    @Test
    void appendsNinjaWindowAfterThePreviousSyncTail() throws IOException {
        String source = Files.readString(SYNC_CODEC);
        int writeTail = source.indexOf("component.getSaintState().writeSync(buf, ownerVisible)");
        int writeNinja = source.indexOf("component.ninjaParryTicks : 0", writeTail);
        int readTail = source.indexOf("component.getSaintState().readSync(buf)", writeNinja);
        int readNinja = source.indexOf("component.ninjaParryTicks =", readTail);

        assertTrue(writeTail >= 0 && writeNinja > writeTail);
        assertTrue(readTail > writeNinja && readNinja > readTail);
    }

    @Test
    void storesNinjaWindowUnderItsOwnNbtKey() throws IOException {
        String source = Files.readString(NBT_CODEC);

        assertTrue(source.contains("tag.putInt(\"NinjaParryTicks\", component.ninjaParryTicks)"));
        assertTrue(source.contains("tag.contains(\"NinjaParryTicks\", NbtElement.NUMBER_TYPE)"));
    }

    @Test
    void ticksNinjaBeforeTheSharedCooldownAndIncludesItInReset() throws IOException {
        String source = Files.readString(COMPONENT);
        int ninjaTick = source.indexOf("NinjaParryRuntime.tick(serverPlayer, this)");
        int cooldownTick = source.indexOf("tickCooldown()", ninjaTick);

        assertTrue(ninjaTick >= 0 && cooldownTick > ninjaTick);
        assertTrue(source.contains("ninjaParryTicks == 0"));
        assertTrue(source.contains("ninjaParryTicks = 0"));
    }
}
