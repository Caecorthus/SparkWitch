package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minecraft adapter for physical Time Stamps. The balance is the stamps in the player's own inventory (hotbar, hidden
 * main slots, offhand) plus the open handler's cursor; the owner's client computes the same value from its synced
 * inventory, so no stamp counter is ever synced.
 * 实体时光邮票的 Minecraft 适配层。余额为玩家自身背包（快捷栏、隐藏主背包、副手）加打开界面光标中的邮票数；
 * 拥有者客户端从已同步的背包计算出相同的值，因此从不同步任何邮票计数。
 */
public final class TimeStampInventory {
    private TimeStampInventory() {
    }

    /** Side-agnostic stamp balance. / 两端通用的邮票余额。 */
    public static int balance(PlayerEntity player) {
        // TODO(WP-05): TimeStampLedger.balance(read(player)).
        return 0;
    }

    /** Server: removes every stamp from inventory 0..40, cursor and open handler slots; returns the count removed. / 服务端：清除全部邮票并返回数量。 */
    public static int stripAll(ServerPlayerEntity player) {
        // TODO(WP-05): strip without giveItemStack/offerOrDrop/insertStack/dropItem.
        return 0;
    }
}
