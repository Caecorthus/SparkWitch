package dev.caecorthus.sparkwitch.roles.civilian.judge;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/** Narrow synchronous cause bridge; queued work must retain its own UUID. / 同步归属桥；异步任务自行保存 UUID。 */
public final class JudgeKillAttribution {
    private static final JudgeAttributionScope<ServerWorld> CAUSES = new JudgeAttributionScope<>();
    private static final ThreadLocal<Attempt> ATTEMPTS = new ThreadLocal<>();

    private JudgeKillAttribution() {}

    /** Restores the previous world/cause even on cancellation or exceptions. / 取消或异常也恢复原世界与归属。 */
    public static void runWith(ServerWorld world, UUID responsible, Runnable action) {
        CAUSES.runWith(world, responsible, action);
    }

    public static void attempt(ServerPlayerEntity victim, ServerPlayerEntity killer, Runnable action) {
        ServerWorld world = victim.getServerWorld();
        UUID cause = CAUSES.actor(world);
        UUID responsible = cause != null ? cause : killer == null ? null : killer.getUuid();
        if (JudgeRuntime.blocksKill(world, responsible, victim.getUuid())) return;

        Attempt previous = ATTEMPTS.get();
        ATTEMPTS.set(new Attempt(world, responsible, victim.getUuid()));
        try {
            // A null-killer death nested in AFTER may be administrative (e.g. Shadow bond), not this cause.
            // AFTER 内嵌的无凶手死亡可能是机制清理，不能继承外层凶手。
            runWith(world, null, action);
        } finally {
            if (previous == null) ATTEMPTS.remove();
            else ATTEMPTS.set(previous);
        }
    }

    /** Called only after Wathe changes the alive ledger to dead. / 仅在 Wathe 真正提交死亡后调用。 */
    public static void committed(ServerPlayerEntity victim) {
        Attempt attempt = ATTEMPTS.get();
        if (attempt == null || attempt.committed || attempt.world != victim.getServerWorld()
                || !attempt.victim.equals(victim.getUuid())) return;
        attempt.committed = true;
        JudgeRuntime.recordKill(attempt.world, attempt.actor, attempt.victim);
        JudgeTrainFallAttribution.superseded(victim);
    }

    private static final class Attempt {
        private final ServerWorld world;
        private final UUID actor;
        private final UUID victim;
        private boolean committed;

        private Attempt(ServerWorld world, UUID actor, UUID victim) {
            this.world = world;
            this.actor = actor;
            this.victim = victim;
        }
    }
}
