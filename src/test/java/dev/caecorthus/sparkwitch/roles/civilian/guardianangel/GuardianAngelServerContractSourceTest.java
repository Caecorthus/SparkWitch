package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianAngelServerContractSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void componentIsOwnerPrivatePersistentAndNeverCopied() throws Exception {
        String component = compact(source("roles/civilian/guardianangel/GuardianAngelPlayerComponent.java"));
        String registry = source("component/SparkWitchComponents.java");
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));

        assertTrue(component.contains("SparkWitch.id(\"guardian_angel\")"));
        assertTrue(component.contains("return recipient == player"));
        assertTrue(component.contains("buf.writeVarInt(state.cooldownTicks())"));
        assertTrue(component.contains("buf.writeUuid(targetUuid)"));
        assertTrue(component.contains("tag.putInt(\"CooldownTicks\""));
        assertTrue(component.contains("tag.putUuid(\"ShieldTarget\""));
        assertTrue(registry.contains("GuardianAngelPlayerComponent.KEY"));
        assertTrue(registry.contains("RespawnCopyStrategy.NEVER_COPY"));
        assertTrue(metadata.contains("\"sparkwitch:guardian_angel\""));
    }

    @Test
    void emptyPacketUsesGuardianIdAndServerRaycast() throws Exception {
        String packet = source("roles/civilian/guardianangel/UseGuardianAngelSkillC2SPacket.java");
        String rules = source("roles/civilian/guardianangel/GuardianAngelRules.java");
        String packets = source("net/SparkWitchPackets.java");
        String targeting = source("roles/civilian/guardianangel/GuardianAngelTargeting.java");

        assertTrue(rules.contains("Identifier.of(\"sparkwitch\", \"guardian\")"));
        assertTrue(packet.contains("record UseGuardianAngelSkillC2SPacket()"));
        assertTrue(packet.contains("PAYLOAD_ID = GuardianAngelRules.SKILL_ID"));
        assertTrue(packets.contains("GuardianAngelFeatureService.use(context.player())"));
        assertTrue(targeting.contains("ProjectileUtil.raycast"));
        assertTrue(targeting.contains("GuardianAngelRules.TARGET_RANGE_SQUARED"));
        assertFalse(packet.contains("UUID"));
    }

    @Test
    void serverOwnsHiddenEffectProtectionReplayAndReconnectExpiry() throws Exception {
        String service = compact(source("roles/civilian/guardianangel/GuardianAngelFeatureService.java"));
        String effects = source("roles/civilian/guardianangel/GuardianAngelEffects.java");
        String events = source("impl/SparkWitchEvents.java");

        assertTrue(effects.contains("SparkWitch.id(\"guardian_shield\")"));
        assertTrue(service.contains("GuardianAngelRules.SHIELD_DURATION_TICKS, 0, false, false, false"));
        assertTrue(service.contains("KillPlayer.BEFORE.register"));
        assertTrue(service.contains("BlackoutEffect.BEFORE.register"));
        assertTrue(service.contains("WatheSounds.ITEM_PSYCHO_ARMOUR"));
        assertTrue(service.contains("SoundCategory.MASTER"));
        assertTrue(service.contains("SHIELD_ACTIVATED_EVENT"));
        assertTrue(service.contains("data.putString(\"death_reason\""));
        assertTrue(service.contains("registerGlobalEventFormatter"));
        assertFalse(service.contains("death_blocked"));
        assertTrue(service.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertTrue(service.contains("clearReconnectedTarget"));
        assertTrue(service.contains("message.sparkwitch.guardian_angel.already_shielded"));
        assertTrue(events.contains("GuardianAngelFeatureService.register()"));
    }

    @Test
    void promotionReconnectFallAndVoiceUseExplicitGuardianHooks() throws Exception {
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        String voice = source("voice/SparkWitchVoiceChatPlugin.java");

        assertTrue(lifecycle.contains("GuardianAngelFeatureService.initializeForPromotion(player, role)"));
        assertTrue(lifecycle.contains("GuardianAngelFeatureService.resumePlayer(player)"));
        assertTrue(lifecycle.contains("GuardianAngelFeatureService.detachPlayer(player)"));
        assertTrue(lifecycle.contains("TrainVoicePlugin.addPlayer(player.getUuid())"));
        assertTrue(voice.contains("GuardianAngelRules.shouldBlockWraithMicrophone"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }
}
