package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Transfer rules for the drop-guard mixins: Control Expert items never leave their holder's own inventory slots.
 * Items are identified by class, so the rules stay safe before registry lookups. The slot-click rule mirrors
 * the Bell Ringer's on purpose (duplicated, not shared, so neither role module depends on the other).
 * Creative players are exempt.
 * 防丢弃 mixin 使用的转移规则：控场专家道具不会离开持有者自身的背包栏位。按物品类识别，因此不依赖注册表；
 * 槽位点击规则刻意与敲钟人一致（复制而非共享，两个职业模块互不依赖）。创造模式玩家不受限制。
 */
public final class ControlExpertInventoryRules {
    private ControlExpertInventoryRules() {
    }

    public static boolean isControlExpertItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && isControlExpertItem(stack.getItem());
    }

    static boolean isControlExpertItem(@Nullable Item item) {
        return item instanceof DisruptorItem || item instanceof TaserItem || item instanceof ShockDeviceItem;
    }

    /** Blocks dropping the selected stack into an item entity. / 阻止把选中的物品丢成掉落物。 */
    public static boolean blocksDrop(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        return player != null && blocksDrop(player.isCreative(), isControlExpertItem(stack));
    }

    static boolean blocksDrop(boolean creative, boolean controlExpertItem) {
        return !creative && controlExpertItem;
    }

    /**
     * Blocks throw, clone, swap into a foreign slot, and moves out of the holder's own inventory slots.
     * 阻止抛出、复制、交换进外部栏位，以及移出持有者自身背包栏位的操作。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        boolean validSlot = slotIndex >= 0 && slotIndex < handler.slots.size();
        Slot clicked = validSlot ? handler.slots.get(slotIndex) : null;
        boolean swapSource = actionType == SlotActionType.SWAP
                && button >= 0 && button < player.getInventory().size()
                && isControlExpertItem(player.getInventory().getStack(button));
        return blocksSlotClick(new SlotClick(
                player.isCreative(),
                actionType,
                isControlExpertItem(handler.getCursorStack()),
                validSlot,
                clicked != null && isControlExpertItem(clicked.getStack()),
                clicked != null && clicked.inventory == player.getInventory(),
                handler == player.playerScreenHandler,
                swapSource));
    }

    static boolean blocksSlotClick(SlotClick click) {
        if (click.creative()) {
            return false;
        }
        // Number-key/offhand swap of the item into a foreign slot. / 数字键或副手交换把道具换入外部栏位。
        if (click.action() == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceHoldsItem()) {
            return true;
        }
        if (!click.cursorHoldsItem() && !click.slotHoldsItem()) {
            return false;
        }
        if (click.action() == SlotActionType.THROW || click.action() == SlotActionType.CLONE) {
            return true;
        }
        if (click.action() == SlotActionType.QUICK_MOVE) {
            return !click.ownInventoryScreen() || !click.playerSlot();
        }
        return !click.validSlot() || !click.playerSlot();
    }

    /**
     * Side-effect-free facts about one slot click. / 单次槽位点击的无副作用事实。
     *
     * @param playerSlot         the clicked slot belongs to the holder's own PlayerInventory
     * @param ownInventoryScreen the open handler is the holder's own inventory screen (no container open)
     */
    record SlotClick(boolean creative, SlotActionType action, boolean cursorHoldsItem, boolean validSlot,
                     boolean slotHoldsItem, boolean playerSlot, boolean ownInventoryScreen,
                     boolean swapSourceHoldsItem) {
    }
}
