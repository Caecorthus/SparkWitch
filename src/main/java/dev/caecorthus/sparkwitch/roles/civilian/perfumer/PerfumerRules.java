package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

/**
 * Pure Perfumer tuning and predicates shared by its server and client seams.
 * 调香师的纯数值与判断集中在这里，服务端与客户端接线只消费这些稳定规则。
 */
public final class PerfumerRules {
    public static final String ROLE_ID = "sparkwitch:perfumer";
    public static final String PERFUME_ESSENCE_ID = "sparkwitch:perfume_essence";
    public static final String COLOGNE_ID = "sparkwitch:cologne";
    public static final String PERFUME_ESSENCE_ENTRY_ID = "perfume_essence";
    public static final String COLOGNE_ENTRY_ID = "cologne";

    public static final int ROLE_COLOR = 0xF2A4A4;
    public static final int BLOODY_OUTLINE_COLOR = 0xC13838;
    public static final int CORPSE_OUTLINE_COLOR = 0xD8D8D8;
    public static final int PERFUME_ESSENCE_PRICE = 100;
    public static final int COLOGNE_PRICE = 50;
    public static final int TASK_REWARD = 50;

    public static final double VISIBLE_OUTLINE_RANGE_BLOCKS = 12.0D;
    public static final double WALL_OUTLINE_RANGE_BLOCKS = 4.0D;
    public static final double CORPSE_SANITY_RANGE_BLOCKS = 4.0D;
    public static final double COLOGNE_RANGE_BLOCKS = 3.0D;

    public static final int COLOGNE_DURATION_TICKS = 200;
    public static final int COLOGNE_PULSE_INTERVAL_TICKS = 20;
    public static final float COLOGNE_MOOD_PER_PULSE = 0.05F;
    public static final float MAX_MOOD = 1.0F;

    private PerfumerRules() {
    }

    public static boolean isWithinVisibleOutlineRange(double squaredDistance) {
        return isWithinRange(squaredDistance, VISIBLE_OUTLINE_RANGE_BLOCKS);
    }

    public static boolean isWithinWallOutlineRange(double squaredDistance) {
        return isWithinRange(squaredDistance, WALL_OUTLINE_RANGE_BLOCKS);
    }

    public static boolean isWithinCorpseSanityRange(double squaredDistance) {
        return isWithinRange(squaredDistance, CORPSE_SANITY_RANGE_BLOCKS);
    }

    public static boolean isWithinCologneRange(double squaredDistance) {
        return isWithinRange(squaredDistance, COLOGNE_RANGE_BLOCKS);
    }

    public static boolean shouldOutlinePlayer(double squaredDistance, boolean hasLineOfSight) {
        return hasLineOfSight
                ? isWithinVisibleOutlineRange(squaredDistance)
                : isWithinWallOutlineRange(squaredDistance);
    }

    /**
     * Returns one extra copy of the baseline drain whenever at least one corpse is nearby.
     * 附近尸体无论有几具都只额外结算一份基础理智下降，避免叠加。
     */
    public static float extraCorpseMoodDrain(float baselineMoodDrain, int nearbyCorpseCount) {
        return nearbyCorpseCount > 0 ? baselineMoodDrain : 0.0F;
    }

    public static float applyCologneMoodPulse(float currentMood) {
        return Math.min(MAX_MOOD, currentMood + COLOGNE_MOOD_PER_PULSE);
    }

    public static boolean canApplyPerfumeEssence(
            boolean userIsPerfumer,
            boolean userAlive,
            boolean targetAlive,
            boolean samePlayer
    ) {
        return userIsPerfumer && userAlive && targetAlive && !samePlayer;
    }

    public static boolean canApplyCologne(
            boolean userIsPerfumer,
            boolean userAlive,
            boolean targetAlive,
            boolean samePlayer,
            double squaredDistance,
            boolean hasLineOfSight
    ) {
        return userIsPerfumer
                && userAlive
                && targetAlive
                && (samePlayer || hasLineOfSight && isWithinCologneRange(squaredDistance));
    }

    private static boolean isWithinRange(double squaredDistance, double rangeBlocks) {
        return squaredDistance <= rangeBlocks * rangeBlocks;
    }
}
