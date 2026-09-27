package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Transfer rules shared by the Clock and Time Stamps, used by the item-generic binding mixins. Items are identified by
 * class, so these checks stay safe before registry lookups. Duplicated from, never shared with, the Bell Ringer rules.
 * 时钟与时光邮票共用的转移规则，供物品通用绑定 mixin 使用。按物品类识别，因此不依赖注册表获取。
 * 复制而非共享敲钟人的规则。
 */
public final class TimeStealerInventoryRules {
    private TimeStealerInventoryRules() {
    }

    public static boolean isClock(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TimeStealerClockItem;
    }

    public static boolean isStamp(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TimeStampItem;
    }

    public static boolean isBound(ItemStack stack) {
        return isClock(stack) || isStamp(stack);
    }

    /** Server-side slot-click veto for bound items. / 绑定物品的服务端栏位点击否决。 */
    public static boolean blocksSlotClick(PlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        // TODO(WP-03b): pure SlotClick matrix (Bell rules + always block QUICK_MOVE + block offhand SWAP 40).
        return false;
    }

    /** Excludes bound items from Wathe's death-drop loop. / 将绑定物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        // TODO(WP-03b): return isBound(stack).
        return false;
    }
}
