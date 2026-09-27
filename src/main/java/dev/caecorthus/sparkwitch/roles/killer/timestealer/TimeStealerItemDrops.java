package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Drop interception for bound items: true means the drop is cancelled. Stamps use move semantics (the passed stack is
 * emptied, then re-delivered to a living holder) so no drop path can duplicate or silently lose them.
 * 绑定物品的丢弃拦截：返回 true 表示取消丢弃。邮票采用移动语义（先清空传入的物品堆，再重新发放给存活持有者），
 * 因此任何丢弃路径都不会复制或悄悄丢失邮票。
 */
public final class TimeStealerItemDrops {
    private TimeStealerItemDrops() {
    }

    public static boolean intercept(PlayerEntity player, ItemStack stack) {
        // TODO(WP-03b): Clock -> true; stamp -> setCount(0) then redeliver to a living holder, true.
        return false;
    }
}
