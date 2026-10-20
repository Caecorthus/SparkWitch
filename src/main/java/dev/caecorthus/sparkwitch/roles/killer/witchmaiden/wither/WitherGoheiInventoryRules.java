package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Bound-item rules of the Wither Gohei: it never leaves its holder's own inventory slots, never drops, and never goes
 * to an item frame, armor stand, allay or decorated pot. Copied from, never shared with, the Potion Gunner rules.
 * 凋灵御币的绑定物品规则：永不离开持有者自身的背包栏位、永不掉落，也不会交给物品展示框、盔甲架、悦灵或饰纹陶罐。
 * 复制而非共享药炮手的规则。
 */
public final class WitherGoheiInventoryRules {
    private WitherGoheiInventoryRules() {
    }

    public static boolean isGohei(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof WitherGoheiItem;
    }

    /** World-interaction veto for the stack in the used hand. / 针对所用手中物品的世界交互否决。 */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isGohei(held) && target != null && takesHeldStack(target.getClass());
    }

    /** Block counterpart of {@link #blocksEntityUse}. / {@link #blocksEntityUse} 的方块版本。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isGohei(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(@Nullable Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /** Excludes the Gohei from Wathe's death-drop loop. / 将御币排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isGohei(stack);
    }

    /** Refuses a selected-slot drop (Q). / 拒绝丢弃选中的御币（Q）。 */
    public static boolean blocksSelectedDrop(@Nullable PlayerEntity player) {
        return player != null && isGohei(player.getInventory().getMainHandStack());
    }

    /** Server-side slot-click veto. / 服务端栏位点击否决。 */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        PlayerInventory inventory = player.getInventory();
        boolean validSlot = slotIndex >= 0 && slotIndex < handler.slots.size();
        Slot clicked = validSlot ? handler.slots.get(slotIndex) : null;
        boolean swapSourceGohei = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isGohei(inventory.getStack(button));
        return blocksSlotClick(new SlotClick(
                actionType,
                button,
                isGohei(handler.getCursorStack()),
                validSlot,
                clicked != null && isGohei(clicked.getStack()),
                clicked != null && clicked.inventory == inventory,
                swapSourceGohei,
                clicked != null && clicked.inventory == inventory
                        && clicked.getIndex() == PlayerInventory.OFF_HAND_SLOT));
    }

    /**
     * Pure decision (Potion Gunner variant): the Gohei stays in the holder's own slots, never by shift-click and never
     * in the offhand (invisible in Wathe's UI; it fires from the main hand only).
     * 纯函数判定（药炮手版本）：御币留在持有者自身栏位，不允许 Shift 点击，也不允许进入副手（副手在 Wathe 界面中不可见，
     * 且只从主手射击）。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceGohei()) {
            return true;
        }
        if (action == SlotActionType.SWAP
                && (click.button() == PlayerInventory.OFF_HAND_SLOT || click.offhandSlot())
                && (click.slotGohei() || click.swapSourceGohei())) {
            return true;
        }
        if (!click.cursorGohei() && !click.slotGohei()) {
            return false;
        }
        if (action == SlotActionType.THROW || action == SlotActionType.CLONE
                || action == SlotActionType.QUICK_MOVE) {
            return true;
        }
        if (action == SlotActionType.QUICK_CRAFT && click.cursorGohei()) {
            return true;
        }
        if (action == SlotActionType.PICKUP && click.cursorGohei() && click.offhandSlot()) {
            return true;
        }
        return !click.validSlot() || !click.playerSlot();
    }

    /** Side-effect-free facts about one slot click. / 单次栏位点击的无副作用事实。 */
    record SlotClick(SlotActionType action, int button, boolean cursorGohei, boolean validSlot, boolean slotGohei,
                     boolean playerSlot, boolean swapSourceGohei, boolean offhandSlot) {
    }
}
