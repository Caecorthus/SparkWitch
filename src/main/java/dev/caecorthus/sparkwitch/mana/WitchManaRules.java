package dev.caecorthus.sparkwitch.mana;

import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.doctor4t.wathe.api.Role;

/**
 * Pure mana economy rules for the mana-bearing roles: the three witches, Emma, and the Abyss Listener (which uses the
 * Grand Witch's economy, owner D11).
 * 魔力角色的纯规则集中在这里（三位魔女、艾玛，以及沿用大魔女魔力机制的聆渊者，所有者 D11），避免事件、组件和 HUD 各写一份判断。
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
        return isWitchManaRole(role) || EmmaRules.isEmma(role) || AbyssListenerRules.isAbyssListener(role);
    }

    /**
     * Roles on the Grand Witch's own economy: 20-tick regeneration, natural cap 300, and kill rewards of 50 (generic)
     * or 100 (a witch-mana-role victim). The Abyss Listener joins it (D11) but never as a victim class and never for
     * the Grand-Witch-only accomplice-kill bonus (C6); the Apprentice, Murderous Witch and Emma keep their own numbers.
     * 沿用大魔女魔力机制的职业：每 20 tick 自然恢复、自然上限 300、击杀奖励 50（普通）或 100（魔女魔力职业受害者）。
     * 聆渊者加入其中（D11），但既不作为受害者类别，也不获得仅属于大魔女的共犯击杀奖励（C6）；预备魔女、杀意魔女与艾玛保持各自数值。
     */
    public static boolean usesGrandWitchManaEconomy(Role role) {
        return role != null
                && (role == SparkWitchRoles.grandWitch() || AbyssListenerRules.isAbyssListener(role));
    }

    private static boolean isWitchManaRole(Role role) {
        return role != null
                && (role == SparkWitchRoles.grandWitch()
                || role == SparkWitchRoles.apprenticeWitch()
                || role == SparkWitchRoles.murderousWitch());
    }

    public static boolean canRegenerateNaturally(Role role) {
        return isManaRole(role);
    }

    public static int naturalCap(Role role) {
        if (usesGrandWitchManaEconomy(role)) {
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
        if (usesGrandWitchManaEconomy(role) || EmmaRules.isEmma(role)) {
            return GRAND_WITCH_REGENERATION_INTERVAL_TICKS;
        }
        if (role == SparkWitchRoles.apprenticeWitch()) {
            return APPRENTICE_REGENERATION_INTERVAL_TICKS;
        }
        return REGENERATION_INTERVAL_TICKS;
    }

    public static int taskReward(Role role) {
        return role == SparkWitchRoles.apprenticeWitch() || EmmaRules.isEmma(role) ? APPRENTICE_TASK_REWARD : 0;
    }

    public static int killReward(Role killerRole, Role victimRole) {
        if (usesGrandWitchManaEconomy(killerRole)) {
            return isWitchManaRole(victimRole)
                    ? GRAND_WITCH_WITCH_KILL_REWARD
                    : GRAND_WITCH_GENERIC_KILL_REWARD;
        }
        if (!isWitchManaRole(killerRole)) {
            return 0;
        }
        if (isWitchManaRole(victimRole)) {
            return WITCH_KILL_REWARD;
        }
        return killerRole == SparkWitchRoles.murderousWitch() ? GENERIC_KILL_REWARD : 0;
    }

    /**
     * Mana every living Grand Witch gains when any accomplice (plain or special) kills. Only Grand Witches receive it;
     * an accomplice with its own mana (the Abyss Listener) is paid separately through {@link #killReward}.
     * 任一共犯（普通或特殊）击杀时，每个存活的大魔女获得的魔力。仅大魔女获得；自身拥有魔力的共犯（聆渊者）另经
     * {@link #killReward} 结算。
     */
    public static int grandWitchRewardForAccompliceKill(Role killerRole, Role victimRole) {
        if (!WitchFactionRules.isAccompliceLike(killerRole)) {
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
