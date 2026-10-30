package dev.caecorthus.sparkwitch.item;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.item.ItemStack;

/**
 * Helpers for the per-stack {@code sparkwitch:special} marker. A marked stack survives ordinary stack copies
 * (including component copies) and is distinct from every unmarked stack of the same item.
 * 逐物品堆 {@code sparkwitch:special} 标记的辅助方法。标记会随物品堆的组件复制一起保留，
 * 并与同物品的普通物品堆区分开。
 */
public final class MarkedItems {
    private MarkedItems() {
    }

    /** Marks the stack and returns it for chaining. / 标记该物品堆并返回它，便于链式调用。 */
    public static ItemStack mark(ItemStack stack) {
        stack.set(SparkWitchDataComponents.SPECIAL, true);
        return stack;
    }

    public static boolean isMarked(ItemStack stack) {
        return stack != null && !stack.isEmpty() && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.SPECIAL));
    }

    public static void unmark(ItemStack stack) {
        stack.remove(SparkWitchDataComponents.SPECIAL);
    }
}
