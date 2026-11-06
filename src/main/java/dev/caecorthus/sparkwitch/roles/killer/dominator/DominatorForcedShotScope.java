package dev.caecorthus.sparkwitch.roles.killer.dominator;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-thread scope of the one forced shot (stable contract for the {@code mixin/dominator/} hooks). Every
 * protection and exemption of the forced shooter is keyed on this scope, never on the 1 s sequence window, so a shot,
 * stab or push the marked player makes by hand during the window is judged normally.
 * <ul>
 *   <li>{@link #isFiring}: true only while {@link DominatorForcedShotService#fireNow} runs Wathe's gun receiver for
 *   this shooter. Synchronous consequences (the kill, every {@code KillPlayer.AFTER} listener such as SparkTraits
 *   Conscience) run inside it.</li>
 *   <li>{@link #coversConsequencesOf}: {@link #isFiring}, or a Wathe {@code Scheduler} task queued while firing is
 *   running ({@code SchedulerForcedShotConsequenceMixin} binds such tasks), so short-delayed consequences of the forced
 *   shot keep the shooter's protection as well.</li>
 * </ul>
 * Identity checks against the server entity only: a client-thread caller (integrated server) never matches.
 * 单次强制开枪的服务端线程作用域（{@code mixin/dominator/} 钩子的稳定契约）。被强制开枪者的所有保护与豁免都以此作用域为准，
 * 绝不以 1 秒的序列窗口为准，因此被标记者在窗口内亲手开枪、刺击或推人时照常结算。
 * {@link #isFiring} 仅在 {@link DominatorForcedShotService#fireNow} 为该射手执行 Wathe 枪械接收器期间为真，同步后果（击杀、
 * 所有 {@code KillPlayer.AFTER} 监听器，例如 SparkTraits 良心）都在其中运行；{@link #coversConsequencesOf} 还包括开枪期间
 * 排入 Wathe {@code Scheduler} 的任务在运行时，使强制开枪的短延迟后果同样受保护。只与服务端实体做同一性比较，
 * 客户端线程（集成服务端）调用永远不会匹配。
 */
public final class DominatorForcedShotScope {
    private static @Nullable ServerPlayerEntity firing;
    private static @Nullable ServerPlayerEntity consequenceOf;

    private DominatorForcedShotScope() {
    }

    /** Runs {@code action} as the forced shot of {@code shooter}. / 以 {@code shooter} 的强制开枪身份执行 {@code action}。 */
    static void fire(ServerPlayerEntity shooter, Runnable action) {
        ServerPlayerEntity previous = firing;
        firing = shooter;
        try {
            action.run();
        } finally {
            firing = previous;
        }
    }

    /** True while {@code entity}'s forced shot is inside Wathe's receiver. / {@code entity} 的强制开枪正在接收器内时为真。 */
    public static boolean isFiring(@Nullable Entity entity) {
        return entity != null && entity == firing;
    }

    /** True while any forced shot is inside Wathe's receiver. / 任意强制开枪正在接收器内时为真。 */
    public static boolean isFiringAny() {
        return firing != null;
    }

    /**
     * True while {@code entity}'s forced shot or one of its scheduled consequences runs.
     * {@code entity} 的强制开枪或其排定的后果正在运行时为真。
     */
    public static boolean coversConsequencesOf(@Nullable Entity entity) {
        return entity != null && (entity == firing || entity == consequenceOf);
    }

    /**
     * Wathe {@code Scheduler.schedule} seam: a task queued while a forced shot fires keeps that shot's scope when it
     * runs. Any other task is returned unchanged.
     * Wathe {@code Scheduler.schedule} 接缝：强制开枪期间排入的任务在运行时保留该次开枪的作用域；其他任务原样返回。
     */
    public static Runnable bindScheduled(Runnable task) {
        ServerPlayerEntity shooter = firing;
        if (shooter == null || task == null) {
            return task;
        }
        return () -> {
            ServerPlayerEntity previous = consequenceOf;
            consequenceOf = shooter;
            try {
                task.run();
            } finally {
                consequenceOf = previous;
            }
        };
    }
}
