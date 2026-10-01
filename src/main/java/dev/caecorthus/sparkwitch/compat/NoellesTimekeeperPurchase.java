package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * NoellesRoles reduce-time item identity only ({@code noellesroles:timekeeper_reduce_time}); no role, faction or role
 * module dependency. Fails closed.
 * 仅负责 NoellesRoles 减少时间物品的身份（{@code noellesroles:timekeeper_reduce_time}）；不依赖职业、阵营或职业模块。
 * 失败关闭。
 *
 * <p>External seam: the stable signal is the entry's display item, not the entry id or NoellesRoles' anonymous
 * {@code TimekeeperShopHandler$1} class. The SparkStrength Coroner's copy of the entry shows the same item and matches
 * here on purpose; it is excluded later by the exact-role gate, never by this class.
 * 外部接缝：稳定信号是条目的展示物品，而不是条目 id 或 NoellesRoles 的匿名类 {@code TimekeeperShopHandler$1}。
 * SparkStrength 验尸官复制的条目展示同一物品，在此处有意匹配；它随后由精确职业门排除，而不是由本类排除。
 */
public final class NoellesTimekeeperPurchase {
    /** Registered by NoellesRoles {@code ModItems} (ASM-pinned). / 由 NoellesRoles {@code ModItems} 注册（ASM 钉住）。 */
    private static final Identifier REDUCE_TIME_ITEM = Identifier.of(NoellesRoleIds.NAMESPACE, "timekeeper_reduce_time");

    private NoellesTimekeeperPurchase() {
    }

    /** Pure id comparison; {@code null} never matches. / 纯 id 比较；{@code null} 永不匹配。 */
    public static boolean matchesItemId(@Nullable Identifier itemId) {
        return REDUCE_TIME_ITEM.equals(itemId);
    }

    /**
     * True only when the entry's display stack is a non-empty reduce-time item; a null entry or stack fails closed.
     * 仅当条目展示物品为非空的减少时间物品时为 true；条目或物品为 null 时失败关闭。
     */
    public static boolean isTimeReductionEntry(@Nullable ShopEntry entry) {
        if (entry == null) {
            return false;
        }
        ItemStack stack = entry.stack();
        return stack != null && !stack.isEmpty() && matchesItemId(Registries.ITEM.getId(stack.getItem()));
    }
}
