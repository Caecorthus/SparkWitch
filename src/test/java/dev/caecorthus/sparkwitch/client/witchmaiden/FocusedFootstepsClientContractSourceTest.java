package dev.caecorthus.sparkwitch.client.witchmaiden;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusedFootstepsClientContractSourceTest {
    private static final Path CLIENT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/witchmaiden");

    @Test
    void inventoryUiUsesWatheScreenAndExistingTargetPacketWithoutSparkStrength() throws IOException {
        String ui = read("WitchMaidenInventoryUi.java");
        String target = read("WitchMaidenTargetWidget.java");

        assertTrue(ui.contains("LimitedInventoryScreen"));
        assertTrue(ui.contains("GameWorldComponent"));
        assertTrue(ui.contains("hasAnyRole"));
        assertTrue(ui.contains("isPlayerDead"));
        assertTrue(target.contains("UseWitchSkillC2SPacket(Optional.of(targetUuid))"));
        assertFalse(ui.contains("sparkstrength"));
        assertFalse(target.contains("sparkstrength"));
    }

    @Test
    void openInventoryRefreshesOwnerAndTargetAvailabilityEveryRender() throws IOException {
        String ui = read("WitchMaidenInventoryUi.java");

        assertTrue(ui.contains("refreshAvailability();"));
        assertTrue(ui.contains("FocusedFootstepsInventoryRules.ownerEligible("));
        assertTrue(ui.contains("List<UUID> liveCandidates = candidates(player)"));
        assertTrue(ui.contains("hideAll();"));
        assertTrue(ui.contains("widget.setPageVisible(false)"));
    }

    @Test
    void inputControllerForcesOnlyPlanarForwardMovement() throws IOException {
        String input = read("FocusedFootstepsInputController.java");
        String mixin = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/witchmaiden/"
                        + "FocusedFootstepsKeyboardInputMixin.java"));

        assertTrue(input.contains("getStatusEffect(FocusedFootstepsEffects.focusedFootsteps())"));
        assertTrue(input.contains("FocusedFootstepsInputRules.forcedPlanarInput()"));
        assertTrue(input.contains("applyPlanarInput(Input input"));
        assertTrue(input.contains("input.movementForward = decision.movementForward()"));
        assertTrue(input.contains("input.movementSideways = decision.movementSideways()"));
        assertTrue(input.contains("input.pressingBack = false"));
        assertTrue(input.contains("input.sneaking = decision.sneaking()"));
        assertFalse(input.contains("input.jumping = false"));
        assertTrue(mixin.contains("@Mixin(KeyboardInput.class)"));
        assertTrue(mixin.contains("@Inject(method = \"tick\", at = @At(\"TAIL\"))"));
        assertTrue(mixin.contains("FocusedFootstepsInputController.applyPlanarInput("));
        assertFalse(mixin.contains("applySprintPhase"));
    }

    @Test
    void sprintPhaseRunsAfterVanillaSelectionAndBeforeSuperclassMovement() throws IOException {
        String controller = read("FocusedFootstepsInputController.java");
        Path mixinPath = Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/witchmaiden/"
                        + "FocusedFootstepsClientPlayerMovementMixin.java");
        assertTrue(Files.exists(mixinPath), "Missing post-vanilla Focused Footsteps movement mixin");
        String mixin = Files.readString(mixinPath);
        String config = Files.readString(Path.of("src/client/resources/sparkwitch.client.mixins.json"));

        assertTrue(controller.contains("PlayerStaminaComponent.KEY.get(player)"));
        assertTrue(controller.contains("FocusedFootstepsInputRules.shouldSprint("));
        assertTrue(controller.contains("player.setSprinting("));
        assertTrue(mixin.contains("@Mixin(ClientPlayerEntity.class)"));
        assertTrue(mixin.contains("method = \"tickMovement\""));
        assertTrue(mixin.contains("Lnet/minecraft/client/input/Input;tick(ZF)V"));
        assertTrue(mixin.contains("FocusedFootstepsInputController.applyPlanarInput("));
        assertTrue(mixin.contains("value = \"INVOKE\""));
        assertTrue(mixin.contains(
                "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V"));
        assertTrue(mixin.contains("FocusedFootstepsInputController.applySprintPhase("));
        int finalMovementTarget = mixin.indexOf(
                "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V");
        int finalMovementMethod = mixin.indexOf("private void", finalMovementTarget);
        int finalMovementMethodEnd = mixin.indexOf("\n    }", finalMovementMethod);
        assertTrue(finalMovementTarget >= 0
                        && finalMovementMethod > finalMovementTarget
                        && finalMovementMethodEnd > finalMovementMethod,
                "Missing injection method immediately before superclass movement");
        String finalMovementInjection = mixin.substring(finalMovementTarget, finalMovementMethodEnd);
        assertTrue(finalMovementInjection.contains("shift = At.Shift.BEFORE"));
        int finalPlanarInput = finalMovementInjection.indexOf(
                "FocusedFootstepsInputController.applyPlanarInput(");
        int finalSprintPhase = finalMovementInjection.indexOf(
                "FocusedFootstepsInputController.applySprintPhase(");
        assertTrue(finalPlanarInput >= 0,
                "The injection immediately before superclass movement must reapply planar input");
        assertTrue(finalSprintPhase > finalPlanarInput,
                "The final movement injection must apply planar input before sprint phase");
        assertTrue(config.contains("witchmaiden.FocusedFootstepsClientPlayerMovementMixin"));
    }

    @Test
    void hardImmobilizersOutrankFocusedFootstepsMovement() throws IOException {
        String input = read("FocusedFootstepsInputController.java");
        int planarInputMethod = input.indexOf("public static void applyPlanarInput(");
        int sprintPhaseMethod = input.indexOf("public static void applySprintPhase(", planarInputMethod);
        int movementLockMethod = input.indexOf("private static boolean isMovementLocked(", sprintPhaseMethod);

        assertTrue(planarInputMethod >= 0
                        && sprintPhaseMethod > planarInputMethod
                        && movementLockMethod > sprintPhaseMethod,
                "Missing shared hard-immobilizer guard");
        String planarInput = input.substring(planarInputMethod, sprintPhaseMethod);
        String sprintPhase = input.substring(sprintPhaseMethod, movementLockMethod);
        String movementLock = input.substring(movementLockMethod);

        assertTrue(planarInput.contains("if (isMovementLocked(player))"));
        assertTrue(sprintPhase.contains("if (isMovementLocked(player))"));
        assertTrue(sprintPhase.contains("player.setSprinting(false)"));
        assertTrue(movementLock.contains("HunterPlayerComponent.KEY.get(player).isRooted()"));
        assertTrue(movementLock.contains(
                "WitchPlayerComponent.KEY.get(player).isPigChaseFreezeActive()"));
    }

    @Test
    void decodedOwnerComponentSyncAcknowledgesThePendingTarget() throws IOException {
        String module = read("WitchMaidenClientModule.java");
        String mixin = Files.readString(Path.of(
                "src/client/java/dev/caecorthus/sparkwitch/client/mixin/witchmaiden/"
                        + "FocusedFootstepsOwnerSyncMixin.java"));

        assertTrue(module.contains("WitchPlayerComponent.KEY.get(player) != component"));
        assertTrue(module.contains("STATE.acknowledgeOwnerSync("));
        assertTrue(mixin.contains("@Mixin(WitchPlayerComponent.class)"));
        assertTrue(mixin.contains("method = \"applySyncPacket\""));
        assertTrue(mixin.contains("at = @At(\"TAIL\")"));
        assertTrue(mixin.contains("WitchMaidenClientModule.onOwnerSync("));
    }

    @Test
    void roleOwnedUseResultResolvesPendingStateWithoutFabricatingNinetySeconds() throws IOException {
        String module = read("WitchMaidenClientModule.java");
        String state = read("FocusedFootstepsClientState.java");
        String widget = read("WitchMaidenTargetWidget.java");

        assertTrue(module.contains("FocusedFootstepsUseResultS2CPacket.ID"));
        assertTrue(module.contains("STATE.resolveUseResult("));
        assertTrue(state.contains("return normalizedCooldown > 0 ? normalizedCooldown : cooldownTicks"));
        assertFalse(state.contains("? FocusedFootstepsRules.COOLDOWN_TICKS"));
        assertTrue(widget.contains("!WitchMaidenClientModule.state().isRequestPending()"));
    }

    private static String read(String name) throws IOException {
        Path path = CLIENT.resolve(name);
        assertTrue(Files.exists(path), () -> "Missing client source " + path);
        return Files.readString(path);
    }
}
