package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraItem;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Transfer rules for the Seeker drop-guard mixins. Seeker device items (Search Car, Security Camera) cannot be
 * dropped, thrown, duplicated or moved into another inventory; they are identified by class, so the rules stay safe
 * before registry lookups. While a remote session is open, every drop and every slot click is denied (the body is
 * frozen). The slot-click matrix is copied from the Control Expert on purpose (duplicated, not shared, so neither
 * role module depends on the other). Creative players are exempt from the item rules only. Server-authoritative:
 * the mixins run these checks on the server and resync the client's prediction.
 * 搜寻者防丢弃 mixin 使用的转移规则。搜寻者设备物品（搜寻小车、摄像头）不能丢弃、投掷、复制，也不能移入他人栏位；
 * 按物品类识别，因此不依赖注册表。遥控会话打开期间，禁止一切丢弃与槽位点击（本体被冻结）。槽位点击矩阵刻意复制自
 * 控场专家（复制而非共享，两个职业模块互不依赖）。创造模式玩家只豁免物品规则。由服务端裁定：mixin 在服务端执行这些
 * 检查并重新同步客户端的预测。
 */
public final class SeekerInventoryRules {
    private SeekerInventoryRules() {
    }

    public static boolean isProtectedItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && isProtectedItem(stack.getItem());
    }

    static boolean isProtectedItem(@Nullable Item item) {
        return item instanceof SeekerCarItem || item instanceof SeekerCameraItem;
    }

    /**
     * The server's synced session mode; a player without the component is never in a session.
     * 服务端同步的会话模式；没有该组件的玩家永远不在会话中。
     */
    public static boolean isInSession(@Nullable PlayerEntity player) {
        return player != null && SeekerStatusComponent.KEY.maybeGet(player)
                .map(component -> component.sessionMode() != SeekerSessionMode.NONE)
                .orElse(false);
    }

    /** Blocks dropping the selected stack into an item entity. / 阻止把选中的物品丢成掉落物。 */
    public static boolean blocksDrop(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        return player != null && blocksDrop(isInSession(player), player.isCreative(), isProtectedItem(stack));
    }

    static boolean blocksDrop(boolean inSession, boolean creative, boolean protectedItem) {
        return inSession || (!creative && protectedItem);
    }

    /**
     * Blocks every click during a session; otherwise blocks throw, clone, swap into a foreign slot, and moves of a
     * device item out of the holder's own inventory slots.
     * 会话期间阻止一切点击；否则阻止设备物品的抛出、复制、交换进外部栏位，以及移出持有者自身背包栏位的操作。
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
                && isProtectedItem(player.getInventory().getStack(button));
        return blocksSlotClick(new SlotClick(
                isInSession(player),
                player.isCreative(),
                actionType,
                isProtectedItem(handler.getCursorStack()),
                validSlot,
                clicked != null && isProtectedItem(clicked.getStack()),
                clicked != null && clicked.inventory == player.getInventory(),
                handler == player.playerScreenHandler,
                swapSource));
    }

    static boolean blocksSlotClick(SlotClick click) {
        if (click.inSession()) {
            return true;
        }
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
     * @param inSession          the clicker has an open Seeker remote session
     * @param playerSlot         the clicked slot belongs to the holder's own PlayerInventory
     * @param ownInventoryScreen the open handler is the holder's own inventory screen (no container open)
     */
    record SlotClick(boolean inSession, boolean creative, SlotActionType action, boolean cursorHoldsItem,
                     boolean validSlot, boolean slotHoldsItem, boolean playerSlot, boolean ownInventoryScreen,
                     boolean swapSourceHoldsItem) {
    }
}
