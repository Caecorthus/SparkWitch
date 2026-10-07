package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Drop interception for the Blind's bound kit: true means the drop is cancelled, so neither item ever becomes an item
 * entity. Some callers have already removed the stack from its slot while others still reference it, so a ComTac uses
 * move semantics (emptied in place, then re-delivered to a living Blind) and can neither duplicate nor silently vanish
 * from its owner; a cane is simply refused, because the loadout restores the single cane on the next tick.
 * 盲人绑定道具的丢弃拦截：返回 true 表示取消丢弃，因此两件物品都不会变成物品实体。有些调用方已把物品堆移出栏位，
 * 另一些仍在栏位中引用它，因此 ComTac 采用移动语义（原地清空后重新交给存活的盲人），既不会复制也不会从主人身上悄悄消失；
 * 盲杖直接拒绝，因为装备服务会在下一 tick 恢复唯一的盲杖。
 */
public final class BlindItemDrops {
    private BlindItemDrops() {
    }

    /**
     * Called at the HEAD of {@code PlayerEntity.dropItem} on both sides; on the client only the prediction is
     * cancelled and the server decides. A non-Blind's or dying player's ComTac is destroyed, as stripping would remove
     * it anyway.
     * 在双端的 {@code PlayerEntity.dropItem} HEAD 处调用；客户端只取消预测，由服务端裁定。非盲人或濒死玩家的 ComTac
     * 被销毁，因为清理本来也会移除它。
     */
    public static boolean intercept(PlayerEntity player, ItemStack stack) {
        if (!BlindInventoryRules.isBound(stack)) {
            return false;
        }
        if (BlindInventoryRules.isComTac(stack) && player instanceof ServerPlayerEntity serverPlayer) {
            ItemStack moved = stack.copy();
            // Move semantics: empty the passed stack before anything is re-delivered. / 移动语义：重新交付前先清空传入的物品堆。
            stack.setCount(0);
            if (BlindParticipants.isActiveBlind(serverPlayer) && serverPlayer.isAlive()) {
                BlindLoadoutService.redeliverComTac(serverPlayer, moved);
            }
        }
        return true;
    }
}
