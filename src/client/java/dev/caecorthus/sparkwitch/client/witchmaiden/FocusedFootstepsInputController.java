package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsEffects;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

/** Applies forced input and sprinting at their distinct vanilla control-flow boundaries. / 在原版各自的控制流边界应用强制输入与跑步状态。 */
public final class FocusedFootstepsInputController {
    private FocusedFootstepsInputController() {
    }

    public static void applyPlanarInput(Input input, ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        StatusEffectInstance effect = player.getStatusEffect(FocusedFootstepsEffects.focusedFootsteps());
        if (effect == null) {
            return;
        }
        if (isMovementLocked(player)) {
            return;
        }

        FocusedFootstepsInputRules.PlanarInput decision =
                FocusedFootstepsInputRules.forcedPlanarInput();
        input.movementForward = decision.movementForward();
        input.movementSideways = decision.movementSideways();
        input.pressingForward = true;
        input.pressingBack = false;
        input.pressingLeft = false;
        input.pressingRight = false;
        input.sneaking = decision.sneaking();
    }

    public static void applyConsumedMovement(ClientPlayerEntity player) {
        if (player == null
                || player.getStatusEffect(FocusedFootstepsEffects.focusedFootsteps()) == null
                || isMovementLocked(player)) {
            return;
        }

        FocusedFootstepsInputRules.PlanarInput decision =
                FocusedFootstepsInputRules.forcedPlanarInput();
        player.forwardSpeed = decision.movementForward();
        player.sidewaysSpeed = decision.movementSideways();
    }

    public static void applySprintPhase(ClientPlayerEntity player) {
        StatusEffectInstance effect = player.getStatusEffect(FocusedFootstepsEffects.focusedFootsteps());
        if (effect == null) {
            return;
        }
        if (isMovementLocked(player)) {
            player.setSprinting(false);
            return;
        }

        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        player.setSprinting(FocusedFootstepsInputRules.shouldSprint(
                effect.getAmplifier(),
                stamina.isInfiniteStamina(),
                stamina.isExhausted()
        ));
    }

    /** Hard immobilizers outrank Focused Footsteps on the owning client. / 在目标客户端，硬定身优先于专注脚步。 */
    private static boolean isMovementLocked(ClientPlayerEntity player) {
        return HunterPlayerComponent.KEY.get(player).isRooted()
                || WitchPlayerComponent.KEY.get(player).isPigChaseFreezeActive();
    }
}
