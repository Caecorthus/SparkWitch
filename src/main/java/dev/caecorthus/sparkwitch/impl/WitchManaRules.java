package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

/**
 * Pure mana economy rules for the three mana-bearing witch roles.
 * 三类魔力角色的纯规则集中在这里，避免事件、组件和 HUD 各写一份判断。
 */
public final class WitchManaRules {
    public static final int INITIAL_MANA = 0;
    public static final int REGENERATION_INTERVAL_TICKS = 40;

    private static final int DEFAULT_NATURAL_CAP = 100;
    private static final int GRAND_WITCH_NATURAL_CAP = 150;
    private static final int APPRENTICE_TASK_REWARD = 20;
    private static final int GENERIC_KILL_REWARD = 25;
    private static final int WITCH_KILL_REWARD = 50;

    private WitchManaRules() {
    }

    public static boolean isManaRole(Role role) {
        return role != null
                && (role == SparkWitchRoles.grandWitch()
                || role == SparkWitchRoles.apprenticeWitch()
                || role == SparkWitchRoles.murderousWitch());
    }

    public static boolean canRegenerateNaturally(Role role) {
        return role != null
                && (role == SparkWitchRoles.grandWitch()
                || role == SparkWitchRoles.murderousWitch());
    }

    public static int naturalCap(Role role) {
        if (role == SparkWitchRoles.grandWitch()) {
            return GRAND_WITCH_NATURAL_CAP;
        }
        return isManaRole(role) ? DEFAULT_NATURAL_CAP : 0;
    }

    public static int taskReward(Role role) {
        return role == SparkWitchRoles.apprenticeWitch() ? APPRENTICE_TASK_REWARD : 0;
    }

    public static int killReward(Role killerRole, Role victimRole) {
        if (!isManaRole(killerRole)) {
            return 0;
        }
        if (isManaRole(victimRole)) {
            return WITCH_KILL_REWARD;
        }
        return canRegenerateNaturally(killerRole) ? GENERIC_KILL_REWARD : 0;
    }

    public static int grandWitchRewardForAccompliceKill(Role killerRole, Role victimRole) {
        if (killerRole != SparkWitchRoles.accomplice()) {
            return 0;
        }
        return killReward(SparkWitchRoles.grandWitch(), victimRole);
    }

    public static int applyNaturalRegeneration(int currentMana, Role role) {
        if (!canRegenerateNaturally(role)) {
            return Math.max(0, currentMana);
        }
        int current = Math.max(0, currentMana);
        int cap = naturalCap(role);
        return current >= cap ? current : Math.min(cap, current + 1);
    }
}
