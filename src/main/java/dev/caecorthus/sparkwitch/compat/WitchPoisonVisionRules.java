package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

/**
 * Pure rules for SparkWitch roles that can read hidden poison traps.
 * SparkWitch 可额外识别隐藏毒陷阱的纯规则，只授予明确列出的职业。
 */
public final class WitchPoisonVisionRules {
    private WitchPoisonVisionRules() {
    }

    public static boolean canSeeHiddenPoison(Role role) {
        return role != null
                && (role == SparkWitchRoles.murderousWitch()
                || role == SparkWitchRoles.accomplice()
                || role == SparkWitchRoles.grandWitch());
    }
}
