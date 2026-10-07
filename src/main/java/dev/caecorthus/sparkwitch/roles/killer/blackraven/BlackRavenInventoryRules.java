package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
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
 * Item-specific transfer rules for Black Raven's bound items: the secret-free ledger and the Raven Mask.
 * Both stay inside their owner's own inventory slots, never become item entities, and cannot be handed to item
 * frames, armor stands, allays or decorated pots.
 * 黑羽鸦绑定物品（无秘密的账本与鸦羽假面）的转移规则：两者只能留在持有者自己的背包槽位中，不会成为掉落物，
 * 也无法交给物品展示框、盔甲架、悦灵或饰纹陶罐。
 */
public final class BlackRavenInventoryRules {
    private BlackRavenInventoryRules() {
    }

    public static boolean isLedger(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.blackRavenLedger());
    }

    public static boolean isMask(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.blackRavenMask());
    }

    /** Ledger or Raven Mask. / 账本或鸦羽假面。 */
    public static boolean isBound(ItemStack stack) {
        return isLedger(stack) || isMask(stack);
    }

    public static boolean blocksDrop(ItemStack stack) {
        return isBound(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack, and a decorated pot inserts any held item. Without this the bound item would
     * leave the inventory, the per-tick {@code restoreLedgerIfNeeded} would mint a fresh copy each time, and breaking
     * the frame or pot would drop a normal item that reveals a Black Raven is in the round.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品，饰纹陶罐会放入任意手持物品。
     * 否则绑定物品会离开背包，逐刻的 {@code restoreLedgerIfNeeded} 会每次补发新副本，而打破展示框或陶罐会掉出
     * 一件普通物品，暴露本局有黑羽鸦。
     */
    public static boolean blocksEntityUse(ItemStack held, Entity target) {
        return isBound(held) && target != null && takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockBlackRavenItemMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockBlackRavenItemMixin} 调用。
     */
    public static boolean blocksBlockUse(ItemStack held, BlockState target) {
        return isBound(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

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
        boolean cursorBound = isBound(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedBound = clickedSlot != null && isBound(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isBound(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorBound && !clickedBound) {
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
