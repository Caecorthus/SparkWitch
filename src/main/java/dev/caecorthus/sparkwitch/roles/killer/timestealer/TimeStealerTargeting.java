package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side Clock gates: whether the user may use the Clock now, and whether a candidate may be affected.
 * 服务端时钟判定：使用者当前能否使用时钟，以及候选者能否被影响。
 */
public final class TimeStealerTargeting {
    private TimeStealerTargeting() {
    }

    /** Use gate (ACTIVE, exact role, KILLER faction, alive, ready, not silenced, weapon action allowed). / 使用判定。 */
    public static boolean canUse(ServerPlayerEntity user, ItemStack clock) {
        // TODO(WP-03a): implement the plan §2 use conditions; fail closed until then.
        return false;
    }

    /** Target veto (plan N5); ineligible candidates are transparent to the ray. / 目标否决（计划 N5）；不合格者对射线透明。 */
    public static boolean canAffect(ServerPlayerEntity user, ServerPlayerEntity target) {
        // TODO(WP-03a): implement the plan N5 target rules; fail closed until then.
        return false;
    }
}
