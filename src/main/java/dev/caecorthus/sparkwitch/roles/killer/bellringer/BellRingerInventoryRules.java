package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Item-specific transfer rules used by the bell binding mixins (mirrors the Black Raven ledger rules).
 * The bell is identified by its item class, so these rules stay safe before registry lookups.
 * 绑定之钟 mixin 使用的物品转移规则（与黑羽鸦感知册规则对应）；按物品类识别钟，因此不依赖注册表获取。
 */
public final class BellRingerInventoryRules {
    private BellRingerInventoryRules() {
    }

    public static boolean isTollBell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TollBellItem;
    }

    /** Blocks drop/throw into an item entity. / 阻止丢弃或抛出为物品实体。 */
    public static boolean blocksDrop(ItemStack stack) {
        return isTollBell(stack);
    }

    /** Blocks the bell from Wathe's death-drop loop. / 阻止钟进入 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isTollBell(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack, and a decorated pot inserts any held item. Without this the bell would leave the
     * inventory, the per-tick restore in {@link BellRingerLoadoutService} would mint a fresh one each time, and a bell
     * that is hidden in hand would sit on display in the world. Copied from, never shared with, the Time Stealer rules.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品，饰纹陶罐会放入任意手持物品。
     * 否则钟会离开背包，{@link BellRingerLoadoutService} 的逐刻补发会每次生成新的钟，而手持时隐藏的钟会公开摆放在世界中。
     * 复制而非共享窃时者的规则。
     */
    public static boolean blocksEntityUse(ItemStack held, Entity target) {
        return isTollBell(held) && target != null && takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockTollBellMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockTollBellMixin} 调用。
     */
    public static boolean blocksBlockUse(ItemStack held, BlockState target) {
        return isTollBell(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /** Blocks throw, clone, swap, and moves out of the owner's own inventory slots. / 阻止抛出、复制、交换及移出拥有者自身背包栏位。 */
    public static boolean blocksSlotClick(
            PlayerEntity player,
            int slotIndex,
            int button,
            SlotActionType actionType
    ) {
        if (player == null || actionType == null) {
            return false;
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        boolean cursorBell = isTollBell(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedBell = clickedSlot != null && isTollBell(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        // Number-key/offhand swap of the bell into a foreign slot. / 数字键或副手交换把钟换入外部栏位。
        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isTollBell(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorBell && !clickedBell) {
            return false;
        }
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE) {
            return true;
        }
        if (actionType == SlotActionType.QUICK_MOVE) {
            return player.currentScreenHandler != player.playerScreenHandler || !playerSlot;
        }
        return !validSlot || !playerSlot;
    }
}
