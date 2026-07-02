package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchPlayerComponentTest {
    private static final Path COMPONENT_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");

    @Test
    void forcedSkillPersistsThroughNbtWithoutClientRuntimeState() {
        NbtCompound tag = new NbtCompound();

        WitchForcedSkillState.writeToNbt(tag, SparkWitch.id("mighty_force"));

        assertEquals(SparkWitch.id("mighty_force"), WitchForcedSkillState.readFromNbt(tag));
    }

    @Test
    void grandWitchCeremonialSwordTaskProgressPersistsSyncsAndClears() throws IOException {
        String source = Files.readString(COMPONENT_SOURCE);

        assertTrue(source.contains("private int grandWitchCeremonialSwordTasks;"));
        assertTrue(source.contains("recordGrandWitchCeremonialSwordTask()"));
        assertTrue(source.contains("GrandWitchRules.clampCeremonialSwordTaskProgress"));
        assertTrue(source.contains("buf.writeVarInt(visible ? grandWitchCeremonialSwordTasks : 0);"));
        assertTrue(source.contains("grandWitchCeremonialSwordTasks = GrandWitchRules.clampCeremonialSwordTaskProgress(buf.readVarInt());"));
        assertTrue(source.contains("tag.putInt(\"GrandWitchCeremonialSwordTasks\", grandWitchCeremonialSwordTasks);"));
        assertTrue(source.contains("tag.contains(\"GrandWitchCeremonialSwordTasks\", NbtElement.NUMBER_TYPE)"));
        assertTrue(source.contains("grandWitchCeremonialSwordTasks = 0;"));
    }
}
