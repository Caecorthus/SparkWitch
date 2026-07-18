package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative target validation and effect refresh. / 服务端权威目标校验与效果刷新。 */
public final class FocusedFootstepsSkillService {
    private FocusedFootstepsSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity target = context.target();
        if (!WitchMaidenRules.isWitchMaiden(context.role()) || target == null) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }

        boolean validTarget = FocusedFootstepsRules.isValidTarget(
                context.player().getUuid(),
                target.getUuid(),
                !target.isDisconnected(),
                target.getServerWorld() == context.world(),
                context.gameComponent().hasAnyRole(target.getUuid()),
                context.gameComponent().isPlayerDead(target.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(target)
        );
        if (!validTarget) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }

        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(target);
        int amplifier = FocusedFootstepsRules.initialPhase(
                stamina.isInfiniteStamina(),
                stamina.isExhausted()
        ).amplifier();

        target.removeStatusEffect(FocusedFootstepsEffects.focusedFootsteps());
        boolean applied = target.addStatusEffect(new StatusEffectInstance(
                FocusedFootstepsEffects.focusedFootsteps(),
                FocusedFootstepsRules.EFFECT_TICKS,
                amplifier,
                false, false, false
        ));
        return applied
                ? WitchSkillUseResult.success(FocusedFootstepsRules.COOLDOWN_TICKS)
                : WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
    }
}
