package dev.caecorthus.sparkwitch.util.hitscan;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
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
 */
public final class RangedOriginScope {
    /** Marker of an unknown-origin scope (ArrayDeque refuses null). / “来源未知”作用域的标记（ArrayDeque 不接受 null）。 */
    private static final Object UNKNOWN = new Object();
    private static final ThreadLocal<ArrayDeque<Object>> SCOPES = new ThreadLocal<>();

    private RangedOriginScope() {
    }

    /** Runs {@code action} with {@code origin} as the current ranged origin. / 以 {@code origin} 为当前远程来源执行。 */
    public static <T> T call(@Nullable Vec3d origin, Supplier<T> action) {
        ArrayDeque<Object> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            SCOPES.set(scopes);
        }
        scopes.push(isFinite(origin) ? origin : UNKNOWN);
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
        call(origin, () -> {
            action.run();
            return null;
        });
    }

    /**
     * The innermost scope's origin; null outside every scope or inside an unknown-origin one.
     * 最内层作用域的来源；不在任何作用域内或处于来源未知的作用域内时为 null。
     */
    public static @Nullable Vec3d current() {
        ArrayDeque<Object> scopes = SCOPES.get();
        return scopes != null && scopes.peek() instanceof Vec3d origin ? origin : null;
    }

    /**
     * Added by BEAR WP3: whether any scope, known or unknown, is open on this thread. An open unknown-origin scope must
     * stop the ricochet hook from falling back to the shooter's eye. / BEAR WP3 新增：本线程是否打开了任何作用域（已知或
     * 未知）。打开的“来源未知”作用域必须阻止跳弹钩子回退到射手眼睛。
     */
    public static boolean isOpen() {
        ArrayDeque<Object> scopes = SCOPES.get();
        return scopes != null && !scopes.isEmpty();
    }

    private static boolean isFinite(@Nullable Vec3d origin) {
        return origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z);
    }
}
