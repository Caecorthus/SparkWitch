package dev.caecorthus.sparkwitch.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Marks vanilla {@code /give}'s cosmetic drop. After a fully inserted give, {@code GiveCommand} drops its shared count-1
 * template stack and despawns the entity at once, only for the pickup animation; that stack is never a real item, and
 * vanilla later reuses it for the next target and for the success message. Bound-item drop guards that put a refused
 * drop back into the inventory ask {@link #isCosmeticCopy} and cancel that drop without restoring or emptying it, so a
 * give never yields one extra item. The mark is the exact stack object (by identity), so any other drop during the call,
 * even of a bound item, keeps its normal handling. It is per thread, so the client never sees the server's mark.
 * 标记原版 {@code /give} 的装饰性丢弃。物品完全放入后，{@code GiveCommand} 会丢出它共用的数量为 1 的模板物品堆并立即让实体
 * 消失，仅用于播放拾取动画；该物品堆从来不是真实物品，原版之后还会把它用于下一个目标和成功提示。会把被拒丢弃放回背包的
 * 绑定物品防护通过 {@link #isCosmeticCopy} 判断，直接取消这次丢弃，既不放回也不清空它，因此一次给予不会多出一件物品。
 * 标记的是确切的物品堆对象（按引用比较），所以调用期间的其他丢弃（即便是绑定物品）保持原有处理。标记按线程区分，
 * 客户端永远看不到服务端的标记。
 */
public final class GiveCommandDropScope {
    private static final ThreadLocal<Object> COSMETIC = new ThreadLocal<>();

    private GiveCommandDropScope() {
    }

    /** True only for the exact stack of the running cosmetic drop. / 仅对正在进行的装饰性丢弃的那个物品堆返回 true。 */
    public static boolean isCosmeticCopy(@Nullable Object stack) {
        return stack != null && stack == COSMETIC.get();
    }

    /**
     * Runs {@code drop} with {@code stack} marked; nested calls and exceptions restore the caller's mark.
     * 在标记 {@code stack} 的情况下执行 {@code drop}；嵌套调用与异常都会恢复调用者的标记。
     */
    public static <T> T cosmeticDrop(Object stack, Supplier<T> drop) {
        Object previous = COSMETIC.get();
        COSMETIC.set(stack);
        try {
            return drop.get();
        } finally {
            if (previous == null) {
                COSMETIC.remove();
            } else {
                COSMETIC.set(previous);
            }
        }
    }
}
