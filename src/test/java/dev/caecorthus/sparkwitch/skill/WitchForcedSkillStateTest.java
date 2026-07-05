package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitchForcedSkillStateTest {
    @Test
    void forcedSkillPersistsThroughNbtWithoutClientRuntimeState() {
        NbtCompound tag = new NbtCompound();

        WitchForcedSkillState.writeToNbt(tag, SparkWitch.id("mighty_force"));

        assertEquals(SparkWitch.id("mighty_force"), WitchForcedSkillState.readFromNbt(tag));
    }
}
