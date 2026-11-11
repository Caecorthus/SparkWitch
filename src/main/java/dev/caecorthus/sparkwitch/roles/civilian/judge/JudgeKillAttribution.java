package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroVictimFeignService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

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
        // 二阶堂希罗's settlement replay is a kill that committed before any sentence could matter: never refused here.
        // 二阶堂希罗的结算重放是在任何判决生效之前就已落定的击杀：此处绝不拒绝。
        if (!HiroVictimFeignService.isSettling(victim)
                && JudgeRuntime.blocksKill(world, responsible, victim.getUuid())) return;

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

    /**
     * Read-only: the responsible UUID (cause, else killer) of this victim's committed kill still on the stack, e.g.
     * inside {@code KillPlayer.AFTER}; null otherwise. The cause scope itself is masked during the kill, so callers
     * must read this instead. Never changes Judge state.
     * 只读：该受害者仍在调用栈中、且已提交的击杀的责任人（归因优先，否则凶手），例如在
     * {@code KillPlayer.AFTER} 内；否则为 null。击杀期间归因作用域已被屏蔽，因此调用方须读此方法。不改变法官状态。
     */
    public static @Nullable UUID committedResponsible(ServerPlayerEntity victim) {
        Attempt attempt = ATTEMPTS.get();
        if (attempt == null || !attempt.committed || attempt.world != victim.getServerWorld()
                || !attempt.victim.equals(victim.getUuid())) return null;
        return attempt.actor;
    }

    /**
     * Read-only: the responsible UUID (cause, else killer) of this victim's kill attempt still on the stack, whether
     * or not it has committed yet; null otherwise. For hooks inside {@code killPlayer} that run before the death commits
     * (二阶堂希罗's 死亡回归 at HEAD and at the commit point). Never changes Judge state.
     * 只读：该受害者仍在调用栈中的击杀尝试的责任人（归因优先，否则凶手），无论是否已提交；否则为 null。供
     * {@code killPlayer} 内早于死亡提交运行的钩子使用（二阶堂希罗「死亡回归」的 HEAD 与提交点）。不改变法官状态。
     */
    public static @Nullable UUID attemptResponsible(ServerPlayerEntity victim) {
        Attempt attempt = ATTEMPTS.get();
        if (attempt == null || attempt.world != victim.getServerWorld()
                || !attempt.victim.equals(victim.getUuid())) return null;
        return attempt.actor;
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
