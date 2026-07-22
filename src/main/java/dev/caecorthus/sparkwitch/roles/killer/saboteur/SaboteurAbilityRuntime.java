package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.util.function.IntConsumer;

/** Testable activation transaction shared by the packet receiver and server gameplay service. */
final class SaboteurAbilityRuntime {
    private SaboteurAbilityRuntime() {
    }

    static boolean use(
            boolean gameRunning,
            boolean activePromotedSaboteur,
            boolean ready,
            Runnable activateOutage,
            IntConsumer startCooldown
    ) {
        if (!gameRunning || !activePromotedSaboteur || !ready) {
            return false;
        }

        // An empty capture is deliberately indistinguishable from one containing eligible lamps.
        // 空范围也必须与存在合格灯具时一样成功，不能泄露附近灯具信息。
        activateOutage.run();
        startCooldown.accept(SaboteurRules.COOLDOWN_TICKS);
        return true;
    }
}
