package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for bottom-right SparkWitch skill HUD state.
 * SparkWitch 右下角技能 HUD 的纯展示规则。
 */
public final class WitchSkillHudRules {
    private WitchSkillHudRules() {
    }

    public static boolean shouldShowPigChaseCoinRequirement(
            @Nullable Identifier skillId,
            int balance,
            int activeTicks,
            int cooldownTicks
    ) {
        return PigGodRules.PIG_CHASE_ID.equals(skillId)
                && activeTicks <= 0
                && cooldownTicks <= 0
                && balance < PigGodRules.COIN_COST;
    }

    public static boolean shouldShowCeremonialSwordTaskUnlock(
            @Nullable Identifier skillId,
            int completedTasks,
            int activeTicks,
            int cooldownTicks
    ) {
        return GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skillId)
                && activeTicks <= 0
                && !GrandWitchRules.isCeremonialSwordUnlocked(completedTasks);
    }

    public static boolean shouldShowManaRequirement(
            @Nullable Identifier skillId,
            int currentMana,
            int requiredMana,
            int activeTicks,
            int cooldownTicks
    ) {
        return shouldShowManaRequirement(skillId, currentMana, requiredMana, activeTicks, cooldownTicks, 0);
    }

    public static boolean shouldShowManaRequirement(
            @Nullable Identifier skillId,
            int currentMana,
            int requiredMana,
            int activeTicks,
            int cooldownTicks,
            int ceremonialSwordTasks
    ) {
        return skillId != null
                && requiredMana > 0
                && activeTicks <= 0
                && cooldownTicks <= 0
                && !shouldShowCeremonialSwordTaskUnlock(skillId, ceremonialSwordTasks, activeTicks, cooldownTicks)
                && currentMana < requiredMana;
    }
}
