package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetComponentSchemaSourceTest {
    private static final Path COMPONENT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path SYNC = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java");
    private static final Path NBT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java");

    @Test
    void delegatesProphetStateWithoutOwningItsFields() throws IOException {
        String component = readText(COMPONENT);
        assertTrue(component.contains("private final ProphetPlayerState prophetState = new ProphetPlayerState();"));
        assertTrue(component.contains("return prophetState.remainingTicks();"));
        assertTrue(component.contains("return prophetState.isActive();"));
        assertTrue(component.contains("return prophetState.containsBody(bodyUuid);"));
        assertTrue(component.contains("prophetState.begin(durationTicks);"));
        assertTrue(component.contains("prophetState.recordBody(bodyUuid)"));
        assertFalse(component.contains("int deathOmenTicks;"));
        assertFalse(component.contains("deathOmenBodyUuids"));
        int activeWindow = component.indexOf("public int getActiveSkillWindowTicks()");
        int nextMethod = component.indexOf("public boolean hasSkill()", activeWindow);
        assertTrue(activeWindow >= 0 && nextMethod > activeWindow);
        assertTrue(component.substring(activeWindow, nextMethod).contains("prophetState.remainingTicks()"));
    }

    @Test
    void normalFinishStartsDeferredCooldownButCancellationOnlyClearsIt() throws IOException {
        String component = readText(COMPONENT);
        String tick = methodBody(component, "public void tickDeathOmenWindow()", "public void cancelDeathOmenWindow()");
        assertTrue(tick.contains("ProphetPlayerState.TickOutcome.FINISHED"));
        assertTrue(tick.contains("startDeferredCooldownNow();"));

        String cancellation = methodBody(
                component,
                "public void cancelDeathOmenWindow()",
                "/**\n     * Arms cooldown"
        );
        assertTrue(cancellation.contains("if (!prophetState.cancel()) {\n            return;\n        }"));
        assertTrue(cancellation.contains("deferredCooldownTicks = 0;"));
        assertFalse(cancellation.contains("prophetState.isEmpty() && deferredCooldownTicks <= 0"));
        assertFalse(cancellation.contains("prophetState.clear();"));
        assertFalse(cancellation.contains("startDeferredCooldownNow();"));
    }

    @Test
    void apprenticeRuntimeDoesNotClaimProphetDeferredCooldown() throws IOException {
        String component = readText(COMPONENT);
        String apprenticeState = methodBody(
                component,
                "public boolean hasApprenticeWindowState()",
                "public PigChaseState pigChaseState()"
        );

        assertFalse(apprenticeState.contains("deferredCooldownTicks > 0"));
    }

    @Test
    void appendsProphetCodecsAfterTheLiveNinjaTail() throws IOException {
        String sync = readText(SYNC);
        String nbt = readText(NBT);
        int syncWriteNinja = sync.indexOf("buf.writeVarInt(ownerVisible ? component.ninjaParryTicks : 0);");
        int syncWriteProphet = sync.indexOf("component.getProphetState().writeSync(buf, ownerVisible);", syncWriteNinja);
        int syncReadNinja = sync.indexOf("component.ninjaParryTicks =", syncWriteProphet);
        int syncReadProphet = sync.indexOf("component.getProphetState().readSync(buf);", syncReadNinja);
        assertTrue(syncWriteNinja >= 0 && syncWriteProphet > syncWriteNinja);
        assertTrue(syncReadNinja > syncWriteProphet && syncReadProphet > syncReadNinja);

        int nbtWriteNinja = nbt.indexOf("tag.putInt(\"NinjaParryTicks\", component.ninjaParryTicks);");
        int nbtWriteProphet = nbt.indexOf("component.getProphetState().writeNbt(tag);", nbtWriteNinja);
        int nbtReadNinja = nbt.indexOf("component.ninjaParryTicks =", nbtWriteProphet);
        int nbtReadProphet = nbt.indexOf("component.getProphetState().readNbt(tag);", nbtReadNinja);
        assertTrue(nbtWriteNinja >= 0 && nbtWriteProphet > nbtWriteNinja);
        assertTrue(nbtReadNinja > nbtWriteProphet && nbtReadProphet > nbtReadNinja);
    }

    private static String readText(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String methodBody(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0 && endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }
}
