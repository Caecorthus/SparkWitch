package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import java.util.function.Supplier;

/** Marks ray shape queries without replacing their entity context. / 标记射线形状查询，不替换其实体上下文。 */
public final class FisherRaycastScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private FisherRaycastScope() {
    }

    public static boolean isRaycast() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Nested queries and exceptions restore the caller's scope on both logical sides.
     * 双端的嵌套查询与异常都会恢复调用者的作用域。 */
    public static <T> T query(Supplier<T> query) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return query.get();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }
}
