package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsRuntimeSourceTest {
    private static final Path ROOT = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/witchmaiden");

    @Test
    void serviceRevalidatesTargetAndRefreshesOneHiddenEffect() throws IOException {
        String service = read("FocusedFootstepsSkillService.java");

        assertTrue(service.contains("FocusedFootstepsRules.isValidTarget"));
        assertTrue(service.contains("context.gameComponent().hasAnyRole"));
        assertTrue(service.contains("context.gameComponent().isPlayerDead"));
        assertTrue(service.contains("target.removeStatusEffect(FocusedFootstepsEffects.focusedFootsteps())"));
        assertTrue(service.contains("false, false, false"));
        assertFalse(service.contains("squaredDistanceTo"));
        assertFalse(service.contains("canSee("));
    }

    @Test
    void effectOwnsRunWalkPhaseAndMoodDrainWithoutSharedPlayerFields() throws IOException {
        String effect = read("FocusedFootstepsEffect.java");

        assertTrue(effect.contains("PlayerStaminaComponent.KEY"));
        assertTrue(effect.contains("PlayerMoodComponent.KEY"));
        assertTrue(effect.contains("GameConstants.MOOD_DRAIN"));
        assertTrue(effect.contains("setSprinting"));
        assertTrue(effect.contains("FocusedFootstepsRules.Phase.fromAmplifier"));
    }

    @Test
    void lifecycleClearsOnlyTheAffectedTargetAtEveryTerminalBoundary() throws IOException {
        String runtime = read("FocusedFootstepsRuntime.java");

        assertTrue(runtime.contains("KillPlayer.AFTER.register"));
        assertTrue(runtime.contains("ResetPlayer.EVENT.register"));
        assertTrue(runtime.contains("GameEvents.ON_FINISH_FINALIZE.register"));
        assertTrue(runtime.contains("ServerPlayConnectionEvents.DISCONNECT.register"));
        assertTrue(runtime.contains("player.removeStatusEffect(FocusedFootstepsEffects.focusedFootsteps())"));
        assertFalse(runtime.contains("caster"));
        assertFalse(runtime.contains("WitchPlayerComponent"));
    }

    @Test
    void requestWrapperKeepsGenericDispatchAndSendsAnOwnerOnlyAcknowledgement() throws IOException {
        String request = read("FocusedFootstepsRequestService.java");
        String packets = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/net/SparkWitchPackets.java"));

        assertTrue(request.contains("boolean accepted = WitchSkillUseService.use(player, targetUuid)"));
        assertTrue(request.contains("WitchMaidenRules.isWitchMaiden(game.getRole(player))"));
        assertTrue(request.contains(
                "WitchPlayerComponent.KEY.syncWith(player, (ComponentProvider) player)"
        ));
        assertTrue(request.contains("new FocusedFootstepsUseResultS2CPacket("));
        assertTrue(request.contains("ServerPlayNetworking.send(player"));
        assertTrue(request.contains("return accepted"));
        assertTrue(request.indexOf("WitchSkillUseService.use(player, targetUuid)")
                == request.lastIndexOf("WitchSkillUseService.use(player, targetUuid)"));
        assertTrue(packets.contains("FocusedFootstepsRequestService.use("));
        assertTrue(packets.contains("FocusedFootstepsUseResultS2CPacket.CODEC"));
        assertFalse(packets.contains("WitchSkillUseService.use(context.player(), payload.targetUuid())"));
    }

    private static String read(String name) throws IOException {
        Path path = ROOT.resolve(name);
        assertTrue(Files.exists(path), () -> "Missing runtime source " + path);
        return Files.readString(path);
    }
}
