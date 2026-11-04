package dev.caecorthus.sparkwitch.roles.killer.dominator;

/**
 * Server-thread guard that suppresses kill money for the synchronous forced shot. Wathe adds every kill reward
 * inside {@code GameFunctions.killPlayer}, which runs inside the receiver call.
 * 服务端线程保护：压制强制开枪这一枪的击杀奖金。Wathe 的所有击杀奖金都在 {@code GameFunctions.killPlayer}
 * 内同步发放，而该调用发生在接收器调用内部。
 */
public final class DominatorForcedShotRewards {
    private static boolean suppressing;

    private DominatorForcedShotRewards() {
    }

    public static void runSuppressed(Runnable action) {
        boolean previous = suppressing;
        suppressing = true;
        try {
            action.run();
        } finally {
            suppressing = previous;
        }
    }

    public static boolean isSuppressing() {
        return suppressing;
    }
}
