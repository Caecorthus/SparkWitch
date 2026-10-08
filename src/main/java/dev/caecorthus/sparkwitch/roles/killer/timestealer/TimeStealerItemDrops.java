package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.util.GiveCommandDropScope;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Drop interception for bound items: true means the drop is cancelled. Stamps use move semantics (the passed stack is
 * emptied, then re-delivered to a living holder) so no drop path can duplicate or silently lose them.
 * 绑定物品的丢弃拦截：返回 true 表示取消丢弃。邮票采用移动语义（先清空传入的物品堆，再重新发放给存活持有者），
 * 因此任何丢弃路径都不会复制或悄悄丢失邮票。
 */
public final class TimeStealerItemDrops {
    private TimeStealerItemDrops() {
    }

    /**
     * Called at the HEAD of the player drop method on both sides. Some callers (the server's selected-slot drop, the
     * full-inventory fallback of an offer) have already removed the stack from its slot, while others still reference
     * it from a slot; zeroing it first covers both without a duplicate. The Clock and the Gift Watch are simply
     * refused: the loadout restores each single watch on the next tick. On the client only the prediction is cancelled; the server decides.
     * 在双端的玩家丢弃方法 HEAD 处调用。有些调用方（服务端丢弃选中栏位、放入背包失败时的回退）已把物品堆移出栏位，
     * 另一些仍在栏位中引用它；先清零即可同时覆盖两种情况且不产生复制。时钟与赠时怀表直接拒绝：装备服务会在下一 tick 恢复各自唯一的怀表。
     * 客户端只取消预测，由服务端裁定。
     * Exception: vanilla {@code /give}'s cosmetic count-1 copy ({@link GiveCommandDropScope}) is only cancelled, never
     * re-delivered or emptied, so a give yields exactly the requested count and its success message keeps the item name.
     * 例外：原版 {@code /give} 的装饰性数量为 1 的副本（{@link GiveCommandDropScope}）只取消丢弃，既不重新发放也不清空，
     * 因此一次给予恰好得到所请求的数量，成功提示也保留物品名称。
     */
    public static boolean intercept(PlayerEntity player, ItemStack stack) {
        if (!TimeStealerInventoryRules.isBound(stack)) {
            return false;
        }
        if (TimeStealerInventoryRules.isClock(stack) || TimeStealerInventoryRules.isGiftWatch(stack)) {
            return true;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (GiveCommandDropScope.isCosmeticCopy(stack)) {
                return true;
            }
            int count = stack.getCount();
            // Move semantics: empty the passed stack before anything is re-delivered. / 移动语义：重新发放前先清空传入的物品堆。
            stack.setCount(0);
            // A non-holder's or dying player's stamps are destroyed, as death and role loss clear stamps anyway.
            // 非持有者或濒死玩家的邮票被销毁，因为死亡与失去职业本来就会清空邮票。
            if (TimeStealerStampService.mayHold(serverPlayer) && serverPlayer.isAlive()) {
                TimeStealerStampService.redeliver(serverPlayer, count);
            }
        }
        return true;
    }
}
