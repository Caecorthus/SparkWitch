package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
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

    /**
     * Server-side slot-click veto for bound items. Gathers side-effect-free facts from the live handler and decides
     * through the pure {@link #blocksSlotClick(SlotClick)} core. There is no creative exemption (Bell parity), so an
     * admin's given Clock or stamps stay bound too.
     * 绑定物品的服务端栏位点击否决。从当前界面收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
     * 没有创造模式豁免（与敲钟人一致），因此管理员给予的时钟或邮票同样受绑定约束。
     */
    public static boolean blocksSlotClick(PlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        PlayerInventory inventory = player.getInventory();
        boolean validSlot = slotIndex >= 0 && slotIndex < handler.slots.size();
        Slot clicked = validSlot ? handler.slots.get(slotIndex) : null;
        // For SWAP the button is the PlayerInventory index of the other side: hotbar 0..8, or 40 for the offhand.
        // SWAP 时 button 为另一侧的 PlayerInventory 下标：快捷栏 0..8，副手为 40。
        boolean swapSourceBound = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isBound(inventory.getStack(button));
        return blocksSlotClick(new SlotClick(
                actionType,
                button,
                isBound(handler.getCursorStack()),
                validSlot,
                clicked != null && isBound(clicked.getStack()),
                clicked != null && clicked.inventory == inventory,
                swapSourceBound));
    }

    /**
     * Pure decision, checked in order. Beyond the Bell Ringer rules it adds two tightenings: QUICK_MOVE of a bound
     * stack is always refused (in the holder's own screen it can only push a hotbar stack into the hidden main slots
     * 9-35, and Wathe's limited screen never sends it), and SWAP with button 40 involving a bound stack is refused (the
     * offhand is invisible in Wathe's UI). Still allowed: PICKUP (left, or right-click half-split) within the holder's
     * own slots, PICKUP_ALL on an own slot, and hotbar number-key SWAP between own slots.
     * 纯函数判定，按顺序检查。在敲钟人规则之上增加两条收紧：绑定物品的 QUICK_MOVE 一律拒绝（在自身界面中它只会把快捷栏
     * 物品推入隐藏的主背包 9-35 格，而 Wathe 的受限界面从不发送它）；涉及绑定物品的副手 SWAP（button 40）拒绝
     * （副手在 Wathe 界面中不可见）。仍然允许：在自身栏位内的 PICKUP（左键或右键半分）、在自身栏位上的 PICKUP_ALL，
     * 以及自身栏位之间的快捷栏数字键 SWAP。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        // (a) Number-key/offhand swap of a bound item into a foreign slot (Bell). / 数字键或副手交换把绑定物品换入外部栏位（敲钟人）。
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceBound()) {
            return true;
        }
        // (b) Offhand swap involving a bound item, even between own slots. / 涉及绑定物品的副手交换，即使在自身栏位之间。
        if (action == SlotActionType.SWAP && click.button() == PlayerInventory.OFF_HAND_SLOT
                && (click.slotBound() || click.swapSourceBound())) {
            return true;
        }
        // (c) Nothing bound is involved. / 未涉及绑定物品。
        if (!click.cursorBound() && !click.slotBound()) {
            return false;
        }
        // (d) Throw and creative clone. / 抛出与创造模式复制。
        if (action == SlotActionType.THROW || action == SlotActionType.CLONE) {
            return true;
        }
        // (e) Shift-click, always (tightening). / Shift 点击，一律拒绝（收紧）。
        if (action == SlotActionType.QUICK_MOVE) {
            return true;
        }
        // (f) Drag distribution with a bound cursor. / 光标持有绑定物品时的拖拽分配。
        if (action == SlotActionType.QUICK_CRAFT && click.cursorBound()) {
            return true;
        }
        // (g) Anything else must stay inside the holder's own inventory slots (Bell). / 其余操作必须留在持有者自身背包栏位（敲钟人）。
        return !click.validSlot() || !click.playerSlot();
    }

    /** Excludes bound items from Wathe's death-drop loop. / 将绑定物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isBound(stack);
    }

    /**
     * Side-effect-free facts about one slot click; "bound" means a Clock or Time Stamp stack.
     * 单次栏位点击的无副作用事实；“绑定”指时钟或时光邮票物品堆。
     *
     * @param button          the raw click button; for SWAP the other side's PlayerInventory index (40 = offhand)
     * @param cursorBound     the handler's cursor holds a bound stack
     * @param validSlot       the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotBound       the clicked slot holds a bound stack
     * @param playerSlot      the clicked slot belongs to the clicker's own PlayerInventory
     * @param swapSourceBound for SWAP, the PlayerInventory stack at {@code button} is bound
     */
    record SlotClick(SlotActionType action, int button, boolean cursorBound, boolean validSlot, boolean slotBound,
                     boolean playerSlot, boolean swapSourceBound) {
    }
}
