package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * NoellesRoles reduce-time item identity only ({@code noellesroles:timekeeper_reduce_time}); no role, faction or role
 * module dependency. Fails closed.
 * 仅负责 NoellesRoles 减少时间物品的身份（{@code noellesroles:timekeeper_reduce_time}）；不依赖职业、阵营或职业模块。
 * 失败关闭。
 */
public final class NoellesTimekeeperPurchase {
    private NoellesTimekeeperPurchase() {
    }

    public static boolean matchesItemId(@Nullable Identifier itemId) {
        // TODO(WP-04): compare with noellesroles:timekeeper_reduce_time.
        return false;
    }

    public static boolean isTimeReductionEntry(@Nullable ShopEntry entry) {
        // TODO(WP-04): matchesItemId(Registries.ITEM.getId(entry.stack().getItem())).
        return false;
    }
}
