package dev.caecorthus.sparkwitch.component;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchPlayerComponentTest {
    private static final Path COMPONENT_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerComponent.java");
    private static final Path SYNC_CODEC_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerSyncCodec.java");
    private static final Path NBT_CODEC_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/component/WitchPlayerNbtCodec.java");

    @Test
    void componentDelegatesSyncAndNbtSchemasToCodecModules() throws IOException {
        String componentSource = Files.readString(COMPONENT_SOURCE);

        assertTrue(componentSource.contains("WitchPlayerSyncCodec.write(this, buf, recipient);"));
        assertTrue(componentSource.contains("WitchPlayerSyncCodec.read(this, buf);"));
        assertTrue(componentSource.contains("WitchPlayerNbtCodec.write(this, tag);"));
        assertTrue(componentSource.contains("WitchPlayerNbtCodec.read(this, tag);"));
    }

    @Test
    void syncCodecKeepsOwnerOnlyRuntimeFieldsAfterPublicFields() throws IOException {
        String source = Files.readString(SYNC_CODEC_SOURCE);

        assertTrue(source.contains("writeOptionalIdentifier(buf, visible ? component.activeSkillId : null);"));
        assertTrue(source.contains("buf.writeVarInt(visible ? component.cooldownTicks : 0);"));
        assertTrue(source.contains("buf.writeBoolean(visible && component.manaEnabled);"));
        assertTrue(source.contains("buf.writeVarInt(visible && component.manaEnabled ? component.mana : 0);"));
        assertTrue(source.contains("buf.writeVarInt(ownerVisible ? component.mightyForceTicks : 0);"));
        assertTrue(source.contains("buf.writeVarInt(ownerVisible ? component.pigChaseTicks : 0);"));
        assertTrue(source.contains("buf.writeVarInt(ownerVisible ? component.deathRayCharges : 0);"));
    }

    @Test
    void nbtCodecOwnsStableStorageKeysAndDefaults() throws IOException {
        String source = Files.readString(NBT_CODEC_SOURCE);

        assertTrue(source.contains("\"ActiveSkill\""));
        assertTrue(source.contains("WitchForcedSkillState.writeToNbt(tag, component.forcedSkillId);"));
        assertTrue(source.contains("\"CooldownTicks\""));
        assertTrue(source.contains("\"ManaEnabled\""));
        assertTrue(source.contains("\"GrandWitchCeremonialSwordTasks\""));
        assertTrue(source.contains("WitchFactionRules.clampCeremonialSwordTaskProgress"));
        assertTrue(source.contains("\"PigChaseOwnsPsycho\""));
        assertTrue(source.contains("\"DeathRayCharges\""));
    }

    @Test
    void componentClearStillResetsAllRuntimeFamilies() throws IOException {
        String source = Files.readString(COMPONENT_SOURCE);

        assertTrue(source.contains("activeSkillId = null;"));
        assertTrue(source.contains("grandWitchCeremonialSwordTasks = 0;"));
        assertTrue(source.contains("pigChaseOwnsPsycho = false;"));
        assertTrue(source.contains("deathRayCharges = 0;"));
        assertTrue(source.contains("deferredCooldownTicks = 0;"));
    }
}
