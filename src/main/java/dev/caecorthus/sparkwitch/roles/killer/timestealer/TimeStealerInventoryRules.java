package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Transfer rules shared by the Clock, the Gift Watch and Time Stamps, used by the item-generic binding mixins. Items are identified by
 * class, so these checks stay safe before registry lookups. Clicks follow the strict {@link BoundItemRules} policy; the
 * Bell Ringer keeps its own, more permissive rules.
 * 时钟、赠时怀表与时光邮票共用的转移规则，供物品通用绑定 mixin 使用。按物品类识别，因此不依赖注册表获取。
 * 点击采用 {@link BoundItemRules} 的严格策略；敲钟人保留自己更宽松的规则。
 */
public final class TimeStealerInventoryRules {
    private TimeStealerInventoryRules() {
    }

    public static boolean isClock(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TimeStealerClockItem;
    }

    public static boolean isGiftWatch(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TimeGiftWatchItem;
    }

    public static boolean isStamp(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TimeStampItem;
    }

    public static boolean isBound(ItemStack stack) {
        return isClock(stack) || isGiftWatch(stack) || isStamp(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand ({@link BoundItemRules#takesHeldStack}). Without this a
     * Clock would leave the inventory and the per-tick restore would mint a fresh copy each time, and stamps would leave
     * the holder's own slots.
     * 针对所用手中物品的世界交互否决（{@link BoundItemRules#takesHeldStack}）。否则时钟会离开背包，而逐刻补发会每次生成新的
     * 副本；邮票也会离开持有者自身栏位。
     */
    public static boolean blocksEntityUse(ItemStack held, Entity target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockTimeStealerItemMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockTimeStealerItemMixin} 调用。
     */
    public static boolean blocksBlockUse(ItemStack held, BlockState target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /**
     * Server-side slot-click veto for bound items through the strict {@link BoundItemRules} policy. There is no
     * creative exemption (Bell parity), so an admin's given Clock or stamps stay bound too.
     * 通过 {@link BoundItemRules} 严格策略进行的绑定物品服务端栏位点击否决。没有创造模式豁免（与敲钟人一致），因此管理员
     * 给予的时钟或邮票同样受绑定约束。
     */
    public static boolean blocksSlotClick(PlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        return BoundItemRules.blocksStrictSlotClick(player, slotIndex, button, actionType,
                TimeStealerInventoryRules::isBound);
    }

    /** Excludes bound items from Wathe's death-drop loop. / 将绑定物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isBound(stack);
    }
}
