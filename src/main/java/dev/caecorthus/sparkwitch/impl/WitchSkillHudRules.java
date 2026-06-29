package dev.caecorthus.sparkwitch.impl;

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
}
