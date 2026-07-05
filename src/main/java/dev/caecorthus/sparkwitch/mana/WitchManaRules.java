package dev.caecorthus.sparkwitch.mana;

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
    private static final int GRAND_WITCH_REGENERATION_INTERVAL_TICKS = 20;
    private static final int APPRENTICE_REGENERATION_INTERVAL_TICKS = 60;
    private static final int GRAND_WITCH_NATURAL_CAP = 300;
    private static final int MURDEROUS_WITCH_NATURAL_CAP = 150;
    private static final int APPRENTICE_TASK_REWARD = 20;
    private static final int GENERIC_KILL_REWARD = 25;
    private static final int WITCH_KILL_REWARD = 50;
    private static final int GRAND_WITCH_GENERIC_KILL_REWARD = 50;
    private static final int GRAND_WITCH_WITCH_KILL_REWARD = 100;

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
                || role == SparkWitchRoles.apprenticeWitch()
                || role == SparkWitchRoles.murderousWitch());
    }

    public static int naturalCap(Role role) {
        if (role == SparkWitchRoles.grandWitch()) {
            return GRAND_WITCH_NATURAL_CAP;
        }
        if (role == SparkWitchRoles.murderousWitch()) {
            return MURDEROUS_WITCH_NATURAL_CAP;
        }
        return isManaRole(role) ? DEFAULT_NATURAL_CAP : 0;
    }

    public static int regenerationIntervalTicks(Role role) {
        if (!canRegenerateNaturally(role)) {
            return 0;
        }
        if (role == SparkWitchRoles.grandWitch()) {
            return GRAND_WITCH_REGENERATION_INTERVAL_TICKS;
        }
        if (role == SparkWitchRoles.apprenticeWitch()) {
            return APPRENTICE_REGENERATION_INTERVAL_TICKS;
        }
        return REGENERATION_INTERVAL_TICKS;
    }

    public static int taskReward(Role role) {
        return role == SparkWitchRoles.apprenticeWitch() ? APPRENTICE_TASK_REWARD : 0;
    }

    public static int killReward(Role killerRole, Role victimRole) {
        if (!isManaRole(killerRole)) {
            return 0;
        }
        if (killerRole == SparkWitchRoles.grandWitch()) {
            return isManaRole(victimRole)
                    ? GRAND_WITCH_WITCH_KILL_REWARD
                    : GRAND_WITCH_GENERIC_KILL_REWARD;
        }
        if (isManaRole(victimRole)) {
            return WITCH_KILL_REWARD;
        }
        return killerRole == SparkWitchRoles.murderousWitch() ? GENERIC_KILL_REWARD : 0;
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
