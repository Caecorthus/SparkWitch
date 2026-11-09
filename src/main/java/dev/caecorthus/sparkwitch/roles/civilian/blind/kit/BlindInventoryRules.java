package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.headset.HeadsetSlot;
import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import dev.caecorthus.sparkwitch.roles.civilian.blind.item.ComTacItem;
import dev.caecorthus.sparkwitch.roles.civilian.blind.item.WhiteCaneItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Bound-item rules for the White Cane and the ComTac VIII, used by the item-generic {@code BlindKit*} mixins. Items are
 * identified by class, so the checks are safe before registry lookups. Duplicated from, never shared with, the Time
 * Stealer rules, plus one loosening: the ComTac may be shift-clicked from the hotbar into the empty headset slot of the
 * holder's own inventory screen (E1, where the head-slot quick-equip used to be). Both items stay inside the holder's
 * own slots: the PlayerInventory slots (armor included) and their own headset slot; there is no creative exemption.
 * 盲杖与 ComTac VIII 的绑定物品规则，供物品通用的 {@code BlindKit*} mixin 使用。按物品类识别，因此不依赖注册表获取。
 * 复制而非共享窃时者规则，并放宽一处：在持有者自身背包界面中，ComTac 可以从快捷栏 Shift 点击放入空的耳机槽（E1，取代原先的
 * 头部槽快速穿戴）。两件物品都只能留在持有者自身的栏位：PlayerInventory 栏位（含护甲槽）及其自己的耳机槽；没有创造模式豁免。
 */
public final class BlindInventoryRules {
    private BlindInventoryRules() {
    }

    public static boolean isCane(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof WhiteCaneItem;
    }

    public static boolean isComTac(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ComTacItem;
    }

    public static boolean isBound(ItemStack stack) {
        return isCane(stack) || isComTac(stack);
    }

    /** Excludes the kit from Wathe's death-drop loop. / 将道具排除出 Wathe 的死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isBound(stack);
    }

    /**
     * Server-side slot-click veto. Gathers side-effect-free facts from the live handler and decides through the pure
     * {@link #blocksSlotClick(SlotClick)} core.
     * 服务端栏位点击否决。从当前界面收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
     */
    public static boolean blocksSlotClick(PlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        PlayerInventory inventory = player.getInventory();
        boolean validSlot = slotIndex >= 0 && slotIndex < handler.slots.size();
        Slot clicked = validSlot ? handler.slots.get(slotIndex) : null;
        boolean inventorySlot = clicked != null && clicked.inventory == inventory;
        // "Own slot" also covers the clicker's own headset slot (handler 46 of their playerScreenHandler, E1).
        // “自身栏位”也包括点击者自己的耳机槽（其 playerScreenHandler 的 46 号栏位，E1）。
        boolean playerSlot = inventorySlot || HeadsetSlot.isHeadsetSlotOf(clicked, player);
        // For SWAP the button is the PlayerInventory index of the other side: hotbar 0..8, or 40 for the offhand.
        // SWAP 时 button 为另一侧的 PlayerInventory 下标：快捷栏 0..8，副手为 40。
        boolean swapSourceBound = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isBound(inventory.getStack(button));
        boolean quickEquipHeadset = actionType == SlotActionType.QUICK_MOVE
                && handler == player.playerScreenHandler
                && inventorySlot
                && PlayerInventory.isValidHotbarIndex(clicked.getIndex())
                && isComTac(clicked.getStack())
                && HeadsetSlots.get(player).isEmpty();
        return blocksSlotClick(new SlotClick(
                actionType,
                button,
                isBound(handler.getCursorStack()),
                validSlot,
                clicked != null && isBound(clicked.getStack()),
                playerSlot,
                swapSourceBound,
                // The offhand is also reachable as a handler slot (PlayerScreenHandler index 45 → inventory index 40).
                // 副手也可作为界面栏位被直接点击（PlayerScreenHandler 下标 45 → 背包下标 40）。
                inventorySlot && clicked.getIndex() == PlayerInventory.OFF_HAND_SLOT,
                quickEquipHeadset));
    }

    /**
     * Pure decision, checked in order (Time Stealer rules (a)-(g)): a SWAP of a bound stack into a foreign slot or
     * through the offhand, THROW/CLONE, QUICK_MOVE (except the ComTac quick-equip into an empty headset slot), drag
     * distribution with a bound cursor, and placing a bound cursor on the offhand are refused; anything else must stay
     * inside the holder's own inventory slots, so containers, the crafting grid and clicks outside the screen are
     * refused. Still allowed: PICKUP and PICKUP_ALL on own slots (moving the ComTac between the hotbar and the headset
     * slot) and number-key SWAP between own hotbar/inventory/headset slots.
     * 纯函数判定，按顺序检查（窃时者规则 (a)-(g)）：绑定物品换入外部栏位或经由副手的 SWAP、THROW/CLONE、QUICK_MOVE
     * （ComTac 快速放入空耳机槽除外）、持绑定光标的拖拽分配、把绑定光标放入副手均拒绝；其余操作必须留在持有者自身
     * 栏位，因此容器、合成格与界面外点击均拒绝。仍然允许：在自身栏位上的 PICKUP 与 PICKUP_ALL（在快捷栏与耳机槽之间
     * 移动 ComTac），以及自身快捷栏/背包/耳机槽之间的数字键 SWAP。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceBound()) {
            return true;
        }
        if (action == SlotActionType.SWAP
                && (click.button() == PlayerInventory.OFF_HAND_SLOT || click.offhandSlot())
                && (click.slotBound() || click.swapSourceBound())) {
            return true;
        }
        if (!click.cursorBound() && !click.slotBound()) {
            return false;
        }
        if (action == SlotActionType.THROW || action == SlotActionType.CLONE) {
            return true;
        }
        if (action == SlotActionType.QUICK_MOVE) {
            return !click.quickEquipHeadset();
        }
        if (action == SlotActionType.QUICK_CRAFT && click.cursorBound()) {
            return true;
        }
        if (action == SlotActionType.PICKUP && click.cursorBound() && click.offhandSlot()) {
            return true;
        }
        return !click.validSlot() || !click.playerSlot();
    }

    /**
     * Side-effect-free facts about one slot click; "bound" means a White Cane or ComTac stack.
     * 单次栏位点击的无副作用事实；“绑定”指盲杖或 ComTac 物品堆。
     *
     * @param button          the raw click button; for SWAP the other side's PlayerInventory index (40 = offhand)
     * @param cursorBound     the handler's cursor holds a bound stack
     * @param validSlot       the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotBound       the clicked slot holds a bound stack
     * @param playerSlot      the clicked slot belongs to the clicker's own PlayerInventory (armor slots included) or is
     *                        the clicker's own headset slot
     * @param swapSourceBound for SWAP, the PlayerInventory stack at {@code button} is bound
     * @param offhandSlot     the clicked slot is the clicker's own offhand (PlayerInventory index 40)
     * @param quickEquipHeadset a QUICK_MOVE of a hotbar ComTac in the holder's own screen while the headset slot is
     *                          empty (routed there by {@code mixin.bear.HeadsetPlayerScreenHandlerMixin})
     */
    record SlotClick(SlotActionType action, int button, boolean cursorBound, boolean validSlot, boolean slotBound,
                     boolean playerSlot, boolean swapSourceBound, boolean offhandSlot, boolean quickEquipHeadset) {
    }
}
