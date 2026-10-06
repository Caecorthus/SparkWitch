package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;

/**
 * Pure Apprentice Witch rules for the 2026-10-06 buff (owner decisions D2, D5, D6): graduation after two tasks, Swift
 * Step charges and the Mighty Force misfire outcome.
 * 预备魔女 2026-10-06 增强的纯规则（所有者决定 D2、D5、D6）：两个任务后出师、滑步充能与巨力误杀结果。
 */
public final class ApprenticeRules {
    /** Tasks that graduate an Apprentice (mirrors the Grand Witch's 2-task unlock). / 出师所需任务数。 */
    public static final int GRADUATION_TASKS = 2;
    public static final int SWIFT_STEP_MAX_CHARGES = 2;

    private ApprenticeRules() {
    }

    public static boolean isGraduated(int completedTasks) {
        return completedTasks >= GRADUATION_TASKS;
    }

    /** True only for the task that crosses the threshold. / 只有跨过门槛的那个任务返回 true。 */
    public static boolean graduatesOn(int tasksBefore, int tasksAfter) {
        return !isGraduated(tasksBefore) && isGraduated(tasksAfter);
    }

    /** A charge is ready when its recharge timer has run out. / 计时归零的充能即为可用。 */
    public static int swiftStepCharges(int rechargeA, int rechargeB) {
        return (rechargeA <= 0 ? 1 : 0) + (rechargeB <= 0 ? 1 : 0);
    }

    /** Ticks until the next charge returns; 0 while one is ready. / 距下一层充能恢复的刻数；有可用充能时为 0。 */
    public static int ticksUntilSwiftStepCharge(int rechargeA, int rechargeB) {
        if (swiftStepCharges(rechargeA, rechargeB) > 0) {
            return 0;
        }
        return Math.min(rechargeA, rechargeB);
    }

    public enum MisfireOutcome {
        NONE,
        KILL_ATTACKER,
        FORFEIT_MIGHTY_FORCE
    }

    /**
     * Mighty Force killing an innocent follows Wathe's shoot-innocent punishment (owner D5). Factions are pre-hit
     * snapshots; an intercepted or non-final death never counts, the same gate as the Swordfish stab.
     * 巨力误杀好人沿用 Wathe 的误杀惩罚设置（所有者 D5）。阵营取命中前快照；被拦截或非最终的死亡不算，与剑鱼刺杀相同。
     */
    public static MisfireOutcome misfireOutcome(
            Identifier attackerFaction,
            Identifier victimFaction,
            boolean deadBefore,
            boolean deadAfter,
            boolean intercepted,
            boolean nonFinal,
            GameWorldComponent.ShootInnocentPunishment punishment
    ) {
        if (!FactionIds.CIVILIAN.equals(attackerFaction) || !FactionIds.CIVILIAN.equals(victimFaction)
                || deadBefore || !deadAfter || intercepted || nonFinal) {
            return MisfireOutcome.NONE;
        }
        return punishment == GameWorldComponent.ShootInnocentPunishment.PREVENT_GUN_PICKUP
                ? MisfireOutcome.FORFEIT_MIGHTY_FORCE
                : MisfireOutcome.KILL_ATTACKER;
    }
}
