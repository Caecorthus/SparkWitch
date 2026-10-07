package dev.caecorthus.sparkwitch.client.apprentice;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeRules;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify.PurifyAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Client texts for the Apprentice Witch buff (owner 2026-10-06): the Purify status line drawn above her skill line,
 * Swift Step's charge count and a forfeited Mighty Force. Reads only her owner-synced components.
 * 预备魔女增强（所有者 2026-10-06）的客户端文本：技能行上方的净化状态行、滑步的充能数，以及失效的巨力。只读取同步给本人的组件。
 */
public final class ApprenticeClientPresentation {
    public static final int PURIFY_COLOR = PurifyAbility.COLOR;

    private ApprenticeClientPresentation() {
    }

    /** An Apprentice is the only role that holds one of these skills. / 只有预备魔女会持有这些技能。 */
    public static boolean isApprenticeSkill(@Nullable Identifier skillId) {
        return skillId != null && ApprenticeAbilityCatalog.ABILITY_IDS.contains(skillId);
    }

    /** Purify's state: locked with task progress, cooling down, short on mana, or ready. / 净化状态行。 */
    public static Text purifyLine(PlayerEntity player) {
        ApprenticePlayerComponent apprentice = ApprenticePlayerComponent.KEY.get(player);
        if (!apprentice.isGraduated()) {
            return Text.translatable("hud.sparkwitch.apprentice.purify.locked",
                    apprentice.getCompletedTasks(), ApprenticeRules.GRADUATION_TASKS);
        }
        if (apprentice.getPurifyCooldownTicks() > 0) {
            return Text.translatable("hud.sparkwitch.apprentice.purify.cooldown",
                    seconds(apprentice.getPurifyCooldownTicks()));
        }
        if (WitchPlayerComponent.KEY.get(player).getMana() < PurifyAbility.MANA_COST) {
            return Text.translatable("hud.sparkwitch.apprentice.purify.not_enough_mana", PurifyAbility.MANA_COST);
        }
        return Text.translatable("hud.sparkwitch.apprentice.purify.ready", SecondaryAbilityController.secondaryKeyText());
    }

    /** Replaces the generic line for a forfeited Mighty Force. / 巨力失效时替换通用状态行。 */
    public static @Nullable Text forfeitedLine(PlayerEntity player, Identifier skillId) {
        if (MightyForceAbility.ID.equals(skillId) && ApprenticePlayerComponent.KEY.get(player).isMightyForceForfeited()) {
            return Text.translatable("hud.sparkwitch.skill.mighty_force.forfeited");
        }
        return null;
    }

    /** Swift Step's ready line with its charge count. / 带充能数的滑步就绪行。 */
    public static @Nullable Text readyLine(PlayerEntity player, Identifier skillId) {
        if (!SwiftStepAbility.ID.equals(skillId)) {
            return null;
        }
        return Text.translatable("hud.sparkwitch.skill.swift_step.ready",
                SparkWitchClient.abilityKeyText(),
                ApprenticePlayerComponent.KEY.get(player).getSwiftStepCharges(),
                ApprenticeRules.SWIFT_STEP_MAX_CHARGES);
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}
