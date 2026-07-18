package dev.caecorthus.sparkwitch.roles.special.wraith;

/**
 * Pure transition rules for the Wraith runtime state machine.
 * 冤魂运行时状态机的纯转换规则。
 */
public final class WraithLifecycleRules {
    private static final int REQUIRED_COMPLETIONS = 3;

    private WraithLifecycleRules() {
    }

    public static boolean shouldQueuePromotion(boolean active, boolean alreadyPromoted, int completions) {
        return active && !alreadyPromoted && completions >= REQUIRED_COMPLETIONS;
    }

    public static boolean canAffectPlayer(boolean actorWraith, boolean targetWraith, boolean samePlayer) {
        return samePlayer || (!actorWraith && !targetWraith);
    }

    public static boolean shouldTerminateForFall(boolean active, double playerY, double minimumY) {
        return active && playerY < minimumY;
    }

    public static boolean shouldResume(boolean active, boolean gameRunning, boolean hasRole, boolean markedDead) {
        return active && gameRunning && hasRole && markedDead;
    }
}
