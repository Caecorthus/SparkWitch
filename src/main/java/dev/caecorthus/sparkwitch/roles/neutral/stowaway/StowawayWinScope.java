package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import java.util.function.Supplier;

/**
 * Server-thread scope around Wathe's {@code CheckWinCondition} dispatch. Every {@code isPlayerPlayingAndAlive} call made
 * by any win listener runs inside this scope, where the Stowaway answers false, so the exclusion reaches NoellesRoles,
 * SparkFactionAPI faction wins, SparkWitch moments and every other listener without touching non-win gameplay.
 * Wathe 的 {@code CheckWinCondition} 分发期间的服务器线程作用域。所有胜利监听器调用的
 * {@code isPlayerPlayingAndAlive} 都在此作用域内执行，偷渡客在其中返回 false，因此排除规则覆盖 NoellesRoles、
 * SparkFactionAPI 阵营胜利、SparkWitch 时刻以及其他所有监听器，同时不影响非胜负玩法。
 */
public final class StowawayWinScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private StowawayWinScope() {
    }

    public static <T> T withinWinCheck(Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = DEPTH.get() - 1;
            if (depth <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
