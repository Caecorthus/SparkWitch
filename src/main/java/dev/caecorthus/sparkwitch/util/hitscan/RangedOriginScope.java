package dev.caecorthus.sparkwitch.util.hitscan;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Frozen contract (BEAR WP0): where the ranged attack being settled on this thread came from, for the Rys-T front test
 * (H3, D3). A ranged source (a hitscan ray, a projectile impact, a blast) wraps the code that may reach
 * {@code GameFunctions.killPlayer} in {@link #call}/{@link #run} with its origin (muzzle, projectile position before
 * impact, blast centre); the ricochet hook reads {@link #current()}. Scopes nest (innermost wins) and are restored on
 * exceptions. A null or non-finite origin opens an "unknown origin" scope, so {@link #current()} answers null inside it
 * even when an outer scope is known (D3: unknown counts as "not front"). Server thread only by convention: the deque is
 * per thread, so another thread never sees it. WP3 adds the pushes at the sources.
 * 冻结契约（BEAR WP0）：本线程正在结算的远程攻击来自何处，供 Rys-T 正面判定使用（H3、D3）。远程来源（命中扫描射线、投射物
 * 命中、爆炸）用 {@link #call}/{@link #run} 把可能调用 {@code GameFunctions.killPlayer} 的代码包起来并给出来源点（枪口、命中前的
 * 投射物位置、爆心）；跳弹钩子读取 {@link #current()}。作用域可嵌套（最内层生效），抛出异常时也会恢复。来源为 null 或非有限值
 * 时打开“来源未知”作用域，因此即使外层已知，{@link #current()} 在其内部也返回 null（D3：未知视为“非正面”）。按约定仅限服务端
 * 线程：队列按线程区分，其他线程永远看不到。WP3 在各来源处加入压栈。
 *
 * <p>Added 2026-11-13 (owner: one attack source, one ricochet roll): every scope is also one attack SOURCE, read by
 * {@link #currentSource()}. The two-argument forms give each scope a fresh identity of its own; the
 * {@code source}-taking forms let one source object (a projectile entity's UUID, one shell's token) keep the same
 * identity across several scopes and ticks.
 * 2026-11-13 新增（所有者：一个攻击来源只掷一次跳弹骰）：每个作用域同时是一个攻击“来源”，由 {@link #currentSource()} 读取。
 * 两参形式为每个作用域生成独立的新身份；带 {@code source} 的形式让同一来源对象（投射物实体的 UUID、一发炮弹的令牌）跨多个
 * 作用域与多个刻保持同一身份。</p>
 */
public final class RangedOriginScope {
    /** One open scope: its origin (null = unknown) and its source identity. / 一个打开的作用域：来源点（null 即未知）与来源身份。 */
    private record Frame(@Nullable Vec3d origin, Object source) {
    }

    private static final ThreadLocal<ArrayDeque<Frame>> SCOPES = new ThreadLocal<>();

    private RangedOriginScope() {
    }

    /** Runs {@code action} with {@code origin} as the current ranged origin. / 以 {@code origin} 为当前远程来源执行。 */
    public static <T> T call(@Nullable Vec3d origin, Supplier<T> action) {
        return call(origin, new Object(), action);
    }

    /**
     * Like {@link #call(Vec3d, Supplier)}, with {@code source} as the attack source's identity (equal identities are the
     * same source). / 同 {@link #call(Vec3d, Supplier)}，并以 {@code source} 作为攻击来源的身份（身份相等即同一来源）。
     */
    public static <T> T call(@Nullable Vec3d origin, Object source, Supplier<T> action) {
        Objects.requireNonNull(source, "source");
        ArrayDeque<Frame> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            SCOPES.set(scopes);
        }
        scopes.push(new Frame(isFinite(origin) ? origin : null, source));
        try {
            return action.get();
        } finally {
            scopes.pop();
            if (scopes.isEmpty()) {
                SCOPES.remove();
            }
        }
    }

    public static void run(@Nullable Vec3d origin, Runnable action) {
        run(origin, new Object(), action);
    }

    public static void run(@Nullable Vec3d origin, Object source, Runnable action) {
        call(origin, source, () -> {
            action.run();
            return null;
        });
    }

    /**
     * The innermost scope's origin; null outside every scope or inside an unknown-origin one.
     * 最内层作用域的来源；不在任何作用域内或处于来源未知的作用域内时为 null。
     */
    public static @Nullable Vec3d current() {
        ArrayDeque<Frame> scopes = SCOPES.get();
        Frame innermost = scopes == null ? null : scopes.peek();
        return innermost == null ? null : innermost.origin();
    }

    /**
     * Added 2026-11-13: the innermost scope's attack-source identity, also for an unknown-origin scope; null outside
     * every scope. / 2026-11-13 新增：最内层作用域的攻击来源身份，来源未知的作用域也有；不在任何作用域内时为 null。
     */
    public static @Nullable Object currentSource() {
        ArrayDeque<Frame> scopes = SCOPES.get();
        Frame innermost = scopes == null ? null : scopes.peek();
        return innermost == null ? null : innermost.source();
    }

    /**
     * Added by BEAR WP3: whether any scope, known or unknown, is open on this thread. An open unknown-origin scope must
     * stop the ricochet hook from falling back to the shooter's eye. / BEAR WP3 新增：本线程是否打开了任何作用域（已知或
     * 未知）。打开的“来源未知”作用域必须阻止跳弹钩子回退到射手眼睛。
     */
    public static boolean isOpen() {
        ArrayDeque<Frame> scopes = SCOPES.get();
        return scopes != null && !scopes.isEmpty();
    }

    private static boolean isFinite(@Nullable Vec3d origin) {
        return origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z);
    }
}
