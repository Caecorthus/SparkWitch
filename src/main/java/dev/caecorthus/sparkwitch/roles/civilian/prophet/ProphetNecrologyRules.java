package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
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
import net.minecraft.util.Identifier;

/**
 * Binding rules for the Prophet's Necrology, used by the Prophet-owned mixins in {@code mixin/prophet/}: the book
 * never becomes an item entity, never drops on death, and stays inside its owner's own inventory slots (no
 * containers, no throw, no creative clone). Parallel to, never shared with, the Black Raven ledger rules. The item is
 * identified by class, so these checks are safe before registry lookups.
 * 先知「亡者名录」的绑定规则，供 {@code mixin/prophet/} 中的先知自有 mixin 使用：书本永不成为掉落物、死亡时不掉落，
 * 且只能留在持有者自身的背包栏位中（不能放入容器、不能丢出、不能创造复制）。与黑羽鸦账本规则平行，绝不共享。
 * 按物品类识别，因此不依赖注册表获取。
 */
public final class ProphetNecrologyRules {
    public static final Identifier ITEM_ID = SparkWitch.id("prophet_necrology");

    private ProphetNecrologyRules() {
    }

    public static boolean isNecrology(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ProphetNecrologyItem;
    }

    /** Never becomes an item entity (Q, death, closing a screen with it on the cursor). / 永不成为掉落物。 */
    public static boolean blocksDrop(ItemStack stack) {
        return isNecrology(stack);
    }

    /** Excluded from Wathe's death-drop loop; the death cleanup deletes it afterwards. / 排除出 Wathe 死亡掉落流程，随后由死亡清理删除。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isNecrology(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack, and a decorated pot inserts any held item. Without this the book would leave the
     * inventory and the per-tick restore would mint a fresh copy each time. Chiseled bookshelves and lecterns accept
     * only their book tags, so they never take the Necrology.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品，饰纹陶罐会放入任意手持物品。
     * 否则书本会离开背包，而逐刻补发会每次生成新的副本。雕纹书架与讲台只接受各自的书籍标签，不会拿走名录。
     */
    public static boolean blocksEntityUse(ItemStack held, Entity target) {
        return isNecrology(held) && target != null && takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockProphetNecrologyMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockProphetNecrologyMixin} 调用。
     */
    public static boolean blocksBlockUse(ItemStack held, BlockState target) {
        return isNecrology(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /**
     * Server-side slot-click veto. Gathers side-effect-free facts from the live handler and decides through the pure
     * {@link #blocksSlotClick(SlotClick)} core.
     * 服务端栏位点击否决：从当前界面收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
     */
    public static boolean blocksSlotClick(PlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        PlayerInventory inventory = player.getInventory();
        boolean validSlot = slotIndex >= 0 && slotIndex < handler.slots.size();
        Slot clicked = validSlot ? handler.slots.get(slotIndex) : null;
        // For SWAP the button is the PlayerInventory index of the other side (hotbar 0..8, 40 = offhand).
        // SWAP 时 button 为另一侧的 PlayerInventory 下标（快捷栏 0..8，副手为 40）。
        boolean swapSourceBound = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isNecrology(inventory.getStack(button));
        return blocksSlotClick(new SlotClick(
                actionType,
                isNecrology(handler.getCursorStack()),
                validSlot,
                clicked != null && isNecrology(clicked.getStack()),
                clicked != null && clicked.inventory == inventory,
                swapSourceBound,
                handler == player.playerScreenHandler
        ));
    }

    /**
     * Pure decision, checked in order; same allowances as the Black Raven ledger. Allowed: PICKUP and hotbar SWAP
     * within the owner's own inventory slots, and shift-click only inside the owner's own inventory screen.
     * 纯函数判定，按顺序检查，放行范围与黑羽鸦账本一致：允许在自身背包栏位内的 PICKUP 与快捷栏 SWAP，
     * Shift 点击仅允许在自身背包界面内进行。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        // (a) Number-key swap of the book into a foreign slot. / 数字键交换把书换入外部栏位。
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceBound()) {
            return true;
        }
        // (b) The book is not involved. / 未涉及名录。
        if (!click.cursorBound() && !click.slotBound()) {
            return false;
        }
        // (c) Throw and creative clone. / 丢出与创造模式复制。
        if (action == SlotActionType.THROW || action == SlotActionType.CLONE) {
            return true;
        }
        // (d) Shift-click: only between the owner's own slots in the owner's own inventory screen.
        // Shift 点击：仅允许在自身背包界面中、自身栏位之间。
        if (action == SlotActionType.QUICK_MOVE) {
            return !click.ownInventoryScreen() || !click.playerSlot();
        }
        // (e) Drag distribution with the book on the cursor. / 光标持有名录时的拖拽分配。
        if (action == SlotActionType.QUICK_CRAFT && click.cursorBound()) {
            return true;
        }
        // (f) Anything else must stay inside the owner's own inventory slots. / 其余操作必须留在持有者自身背包栏位。
        return !click.validSlot() || !click.playerSlot();
    }

    /**
     * Side-effect-free facts about one slot click; "bound" means a Necrology stack.
     * 单次栏位点击的无副作用事实；“绑定”指亡者名录物品堆。
     *
     * @param cursorBound        the handler's cursor holds the book
     * @param validSlot          the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotBound          the clicked slot holds the book
     * @param playerSlot         the clicked slot belongs to the clicker's own PlayerInventory
     * @param swapSourceBound    for SWAP, the PlayerInventory stack at the button index is the book
     * @param ownInventoryScreen the open handler is the clicker's own inventory screen
     */
    record SlotClick(SlotActionType action, boolean cursorBound, boolean validSlot, boolean slotBound,
                     boolean playerSlot, boolean swapSourceBound, boolean ownInventoryScreen) {
    }
}
