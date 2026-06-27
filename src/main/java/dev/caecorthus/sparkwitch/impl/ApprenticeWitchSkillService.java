package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side effect entry points for Apprentice Witch active skills.
 * 预备魔女主动技能的服务端入口；公共校验集中处理，避免影响其他魔女职业。
 */
public final class ApprenticeWitchSkillService {
    private ApprenticeWitchSkillService() {
    }

    public static WitchSkillUseResult useMightyForce(WitchSkillUseContext context) {
        return useApprenticeSkill(context, ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_COOLDOWN_TICKS,
                "message.sparkwitch.skill.mighty_force.activated",
                () -> WitchPlayerComponent.KEY.get(context.player())
                        .beginMightyForceWindow(ApprenticeWitchSkillRules.MIGHTY_FORCE_WINDOW_TICKS));
    }

    public static WitchSkillUseResult useSwiftStep(WitchSkillUseContext context) {
        return useApprenticeSkill(context, ApprenticeWitchSkillRules.SWIFT_STEP_MANA_COST,
                ApprenticeWitchSkillRules.SWIFT_STEP_COOLDOWN_TICKS,
                "message.sparkwitch.skill.swift_step.activated",
                () -> context.player().addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED,
                        ApprenticeWitchSkillRules.SWIFT_STEP_DURATION_TICKS,
                        ApprenticeWitchSkillRules.SWIFT_STEP_AMPLIFIER,
                        false,
                        false,
                        true
                )));
    }

    public static WitchSkillUseResult useMurderSense(WitchSkillUseContext context) {
        return useApprenticeSkill(context, ApprenticeWitchSkillRules.MURDER_SENSE_MANA_COST,
                ApprenticeWitchSkillRules.MURDER_SENSE_COOLDOWN_TICKS,
                "message.sparkwitch.skill.murder_sense.activated",
                () -> WitchPlayerComponent.KEY.get(context.player())
                        .beginMurderSense(ApprenticeWitchSkillRules.MURDER_SENSE_DURATION_TICKS));
    }

    public static WitchSkillUseResult useHealing(WitchSkillUseContext context) {
        return useApprenticeSkill(context, ApprenticeWitchSkillRules.HEALING_MANA_COST,
                ApprenticeWitchSkillRules.HEALING_COOLDOWN_TICKS,
                "message.sparkwitch.skill.healing.activated",
                () -> WitchPlayerComponent.KEY.get(context.player())
                        .beginHealingAura(ApprenticeWitchSkillRules.HEALING_DURATION_TICKS));
    }

    public static WitchSkillUseResult useClairvoyance(WitchSkillUseContext context) {
        return useApprenticeSkill(context, ApprenticeWitchSkillRules.CLAIRVOYANCE_MANA_COST,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_COOLDOWN_TICKS,
                "message.sparkwitch.skill.clairvoyance.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginClairvoyance(
                        ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_TICKS,
                        ApprenticeWitchSkillRules.CLAIRVOYANCE_OTHERS_TICKS
                ));
    }

    private static WitchSkillUseResult useApprenticeSkill(
            WitchSkillUseContext context,
            int manaCost,
            int cooldownTicks,
            String successMessageKey,
            Runnable effect
    ) {
        if (context.role() != SparkWitchRoles.apprenticeWitch()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        if (!component.spendMana(manaCost)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }

        effect.run();
        return WitchSkillUseResult.success(cooldownTicks, successMessageKey);
    }

    public static void applyHealingPulse(ServerPlayerEntity caster) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(caster.getServerWorld());
        double rangeSquared = ApprenticeWitchSkillRules.HEALING_RANGE_BLOCKS
                * ApprenticeWitchSkillRules.HEALING_RANGE_BLOCKS;
        for (ServerPlayerEntity target : caster.getServerWorld().getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(target)
                    || caster.squaredDistanceTo(target) > rangeSquared
                    || !gameComponent.isInnocent(target)) {
                continue;
            }
            PlayerMoodComponent moodComponent = PlayerMoodComponent.KEY.get(target);
            moodComponent.setMood(Math.min(1.0f, moodComponent.getMood() + ApprenticeWitchSkillRules.HEALING_MOOD_PER_SECOND));
        }
    }
}
