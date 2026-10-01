package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherItem;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Transfer rules for the bound launcher and the four shells, used by the item-generic binding mixins in
 * {@code mixin/potiongunner/}. Items are identified by class, so these checks stay safe before registry lookups.
 * Duplicated from, never shared with, the Time Stealer rules (same strictest variant): no creative exemption, so an
 * admin's given launcher or shells stay bound too.
 * 绑定炮筒与四种炮弹的转移规则，供 {@code mixin/potiongunner/} 中的物品通用绑定 mixin 使用。按物品类识别，因此不依赖
 * 注册表获取。复制而非共享窃时者的规则（同为最严格的版本）：没有创造模式豁免，管理员给予的炮筒或炮弹同样受绑定约束。
 */
public final class PotionGunnerInventoryRules {
    private PotionGunnerInventoryRules() {
    }

    public static boolean isLauncher(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PotionLauncherItem;
    }

    public static boolean isShell(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PotionShellItem;
    }

    public static boolean isBound(@Nullable ItemStack stack) {
        return isLauncher(stack) || isShell(stack);
    }

    /** Excludes bound items from Wathe's death-drop loop. / 将绑定物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isBound(stack);
    }

    /**
     * Refuses a selected-slot drop (Q) of a bound item before the stack leaves its slot, so a loaded launcher keeps
     * its shell. Wathe already drops the packet for living in-game players; this also covers creative and lobby use.
     * 在物品离开栏位之前拒绝丢弃选中的绑定物品（Q），使已装填的炮筒保留炮弹。Wathe 已为对局中存活玩家丢弃该数据包；
     * 这里同时覆盖创造模式与大厅场景。
     */
    public static boolean blocksSelectedDrop(@Nullable PlayerEntity player) {
        return player != null && isBound(player.getInventory().getMainHandStack());
    }

    /**
     * Server-side slot-click veto for bound items. Gathers side-effect-free facts from the live handler and decides
     * through the pure {@link #blocksSlotClick(SlotClick)} core.
     * 绑定物品的服务端栏位点击否决。从当前界面收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
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
                swapSourceBound,
                // The offhand is also reachable as a handler slot (PlayerScreenHandler index 45 → inventory index 40).
                // 副手也可作为界面栏位被直接点击（PlayerScreenHandler 下标 45 → 背包下标 40）。
                clicked != null && clicked.inventory == inventory
                        && clicked.getIndex() == PlayerInventory.OFF_HAND_SLOT));
    }

    /**
     * Pure decision, checked in order (Time Stealer variant). Bound items never leave the holder's own inventory
     * slots; shift-click is always refused (in the holder's own screen it can only push a hotbar stack into the hidden
     * main slots 9-35, where Wathe's hotbar-only screen can no longer reach it); the offhand refuses bound items by
     * either route (button-40 swap, or a click on the offhand slot itself), because it is invisible in Wathe's UI and
     * the launcher fires from the main hand only. Still allowed: PICKUP (left, or right-click half-split) within the
     * holder's own slots — the shell-on-cursor loading click relies on it — PICKUP_ALL on an own slot, taking a bound
     * stack out of the offhand, and hotbar number-key SWAP between own slots.
     * 纯函数判定，按顺序检查（窃时者版本）。绑定物品不得离开持有者自身的背包栏位；Shift 点击一律拒绝（在自身界面中它只会把
     * 快捷栏物品推入隐藏的主背包 9-35 格，Wathe 仅显示快捷栏的界面无法再触及）；副手通过任一途径（button 40 交换，或直接
     * 点击副手栏位）都拒绝绑定物品，因为副手在 Wathe 界面中不可见，且炮筒只从主手发射。仍然允许：在自身栏位内的 PICKUP
     * （左键或右键半分；光标持炮弹装填的点击依赖于此）、在自身栏位上的 PICKUP_ALL、从副手取出绑定物品，以及自身栏位之间的
     * 快捷栏数字键 SWAP。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        // (a) Number-key/offhand swap of a bound item into a foreign slot. / 数字键或副手交换把绑定物品换入外部栏位。
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceBound()) {
            return true;
        }
        // (b) Offhand swap involving a bound item, even between own slots. / 涉及绑定物品的副手交换，即使在自身栏位之间。
        if (action == SlotActionType.SWAP
                && (click.button() == PlayerInventory.OFF_HAND_SLOT || click.offhandSlot())
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
        // (e) Shift-click, always. / Shift 点击，一律拒绝。
        if (action == SlotActionType.QUICK_MOVE) {
            return true;
        }
        // (f) Drag distribution with a bound cursor. / 光标持有绑定物品时的拖拽分配。
        if (action == SlotActionType.QUICK_CRAFT && click.cursorBound()) {
            return true;
        }
        // (f2) Placing a bound cursor onto the offhand slot. / 把绑定光标放入副手栏位。
        if (action == SlotActionType.PICKUP && click.cursorBound() && click.offhandSlot()) {
            return true;
        }
        // (g) Anything else must stay inside the holder's own inventory slots. / 其余操作必须留在持有者自身背包栏位。
        return !click.validSlot() || !click.playerSlot();
    }

    /**
     * Side-effect-free facts about one slot click; "bound" means a launcher or shell stack.
     * 单次栏位点击的无副作用事实；“绑定”指炮筒或炮弹物品堆。
     *
     * @param button          the raw click button; for SWAP the other side's PlayerInventory index (40 = offhand)
     * @param cursorBound     the handler's cursor holds a bound stack
     * @param validSlot       the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotBound       the clicked slot holds a bound stack
     * @param playerSlot      the clicked slot belongs to the clicker's own PlayerInventory
     * @param swapSourceBound for SWAP, the PlayerInventory stack at {@code button} is bound
     * @param offhandSlot     the clicked slot is the clicker's own offhand (PlayerInventory index 40)
     */
    record SlotClick(SlotActionType action, int button, boolean cursorBound, boolean validSlot, boolean slotBound,
                     boolean playerSlot, boolean swapSourceBound, boolean offhandSlot) {
    }
}
