package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Pure transition rules for the Wraith runtime state machine. */
public final class WraithLifecycleRules {
    private WraithLifecycleRules() {
    }

    public static boolean shouldQueuePromotion(boolean active, boolean alreadyPromoted, int completions) {
        return active && !alreadyPromoted && completions >= 3;
    }

    public static boolean didNewLastStandTrigger(boolean triggeredBefore, boolean triggeredAfter) {
        return !triggeredBefore && triggeredAfter;
    }

    public static boolean canAffectPlayer(boolean actorWraith, boolean targetWraith, boolean samePlayer) {
        return samePlayer || (!actorWraith && !targetWraith);
    }

    public static boolean shouldTerminateForFall(boolean active, double playerY, double minimumY) {
        return active && playerY < minimumY;
    }

    public static boolean shouldResume(boolean active, boolean running, boolean hasRole, boolean markedDead) {
        return active && running && hasRole && markedDead;
    }
}
