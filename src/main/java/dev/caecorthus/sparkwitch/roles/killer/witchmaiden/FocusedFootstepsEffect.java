package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConfirmS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Server-authoritative forced movement; amplifier zero runs and amplifier one permanently walks.
 * 服务端权威强制移动；零级为疾跑，一级为本次效果内永久步行。
 */
public final class FocusedFootstepsEffect extends StatusEffect {
    public FocusedFootstepsEffect() {
        super(StatusEffectCategory.HARMFUL, FocusedFootstepsRules.COLOR);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        FocusedFootstepsRules.Phase phase = FocusedFootstepsRules.Phase.fromAmplifier(amplifier);
        FocusedFootstepsRules.Phase next = FocusedFootstepsRules.nextPhase(
                phase,
                stamina.isInfiniteStamina(),
                stamina.isExhausted()
        );
        if (next != phase) {
            StatusEffectInstance current = player.getStatusEffect(FocusedFootstepsEffects.focusedFootsteps());
            if (current != null) {
                player.addStatusEffect(new StatusEffectInstance(
                        FocusedFootstepsEffects.focusedFootsteps(),
                        current.getDuration(),
                        next.amplifier(),
                        false,
                        false,
                        false
                ));
            }
        }

        boolean rooted = HunterPlayerComponent.KEY.get(player).isRooted();
        boolean pigGodFrozen = WitchPlayerComponent.KEY.get(player).isPigChaseFreezeActive();
        boolean running = next == FocusedFootstepsRules.Phase.RUNNING || stamina.isInfiniteStamina();
        player.setSprinting(FocusedFootstepsMovementRules.shouldSprint(running, rooted, pigGodFrozen));
        if (FocusedFootstepsMovementRules.shouldUseServerFallback(
                ServerPlayNetworking.canSend(player, SparkWitchServerConfirmS2CPacket.ID),
                rooted,
                pigGodFrozen
        )) {
            player.travel(new Vec3d(0.0, 0.0, 1.0));
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(mood.getMood() - GameConstants.MOOD_DRAIN);
        return true;
    }
}
