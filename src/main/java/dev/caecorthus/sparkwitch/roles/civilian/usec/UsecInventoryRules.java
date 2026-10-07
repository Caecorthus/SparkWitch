package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: the five USEC bound items (rifle, magazine, FMJ and AP rounds, suppressor) and the transfer rules
 * the {@code mixin/usec/} bound-item guards ask. Items are identified by class, so every check is safe before registry
 * lookups and on both sides. Bound items never leave their holder's own player inventory (no drop, no foreign slot, no
 * world target, no death drop), but move freely inside it, cursor included: the cursor-round-onto-magazine loading
 * click (WP5) is a PICKUP between own slots. No creative exemption (Potion Gunner variant). The slot-click veto is
 * server-authoritative; the world-use vetoes run on both sides.
 * 稳定契约：五种 USEC 绑定物品（步枪、弹匣、FMJ 与 AP 子弹、消音器），以及 {@code mixin/usec/} 绑定物品守卫所询问的转移规则。
 * 按物品类识别，因此所有判定在注册表查询之前、在双端都安全。绑定物品绝不离开持有者自身的玩家背包（不能丢弃、不能进入外部栏位、
 * 不能交给世界目标、不会死亡掉落），但可以在背包内自由移动（含光标）：光标持子弹右键弹匣的装填点击（WP5）是自身栏位之间的
 * PICKUP。没有创造模式豁免（药炮手版本）。栏位点击否决由服务端裁定；世界交互否决在双端执行。
 */
public final class UsecInventoryRules {
    /** Vanilla quick-craft (drag) type of the creative clone drag. / 原版拖拽分配中创造模式复制拖拽的类型。 */
    static final int CLONE_DRAG = 2;

    private UsecInventoryRules() {
    }

    public static boolean isBound(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && isBoundItem(stack.getItem());
    }

    public static boolean isRifle(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof UsecRifleItem;
    }

    static boolean isBoundItem(@Nullable Item item) {
        return item != null && isBoundType(item.getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean isBoundType(@Nullable Class<?> itemType) {
        return itemType != null
                && (UsecRifleItem.class.isAssignableFrom(itemType)
                || UsecMagazineItem.class.isAssignableFrom(itemType)
                || UsecAmmoItem.class.isAssignableFrom(itemType)
                || UsecSuppressorItem.class.isAssignableFrom(itemType));
    }

    /** Excludes bound items from Wathe's death-drop loop. / 将绑定物品排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isBound(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack. Asked by the {@code UseEntityCallback} in {@code UsecLifecycleService}.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品。由
     * {@code UsecLifecycleService} 中的 {@code UseEntityCallback} 调用。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isBound(held) && target != null && takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}: a decorated pot inserts any held item. Asked by
     * {@code DecoratedPotBlockUsecItemMixin}. / {@link #blocksEntityUse} 的方块版本：饰纹陶罐会放入任意手持物品。由
     * {@code DecoratedPotBlockUsecItemMixin} 调用。
     */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isBound(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(@Nullable Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /**
     * Refuses a selected-slot drop (Q) of a bound item before the stack leaves its slot, so a loaded rifle keeps its
     * state. / 在物品离开栏位之前拒绝丢弃选中的绑定物品（Q），使已装填的步枪保持其状态。
     */
    public static boolean blocksSelectedDrop(@Nullable PlayerEntity player) {
        return player != null && isBound(player.getInventory().getMainHandStack());
    }

    /**
     * Server-side slot-click veto for bound items in {@code handler}. Gathers side-effect-free facts and decides through
     * the pure {@link #blocksSlotClick(SlotClick)} core.
     * {@code handler} 中绑定物品的服务端栏位点击否决。收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
     */
    public static boolean blocksSlotClick(ScreenHandler handler, @Nullable PlayerEntity player, int slotIndex,
                                          int button, @Nullable SlotActionType actionType) {
        if (handler == null || player == null || actionType == null) {
            return false;
        }
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
     * Pure decision, checked in order. Bound items never leave the holder's own inventory slots, but every move inside
     * them stays allowed: PICKUP (left, right-click half split, the cursor loading click), PICKUP_ALL, number-key and
     * offhand SWAP, and drag distribution (QUICK_CRAFT) across own slots. Refused: throw and clone, shift-click of a
     * bound stack (in a container screen it moves into the container; in Wathe's own screen it hides the stack in main
     * slots the screen does not show), the creative clone drag, a drag step onto a foreign slot, and any other click
     * outside the own slots (a click outside the window drops the cursor).
     * 纯函数判定，按顺序检查。绑定物品绝不离开持有者自身的背包栏位，但在其中的一切移动仍然允许：PICKUP（左键、右键半分、光标
     * 装填点击）、PICKUP_ALL、数字键与副手 SWAP，以及在自身栏位间的拖拽分配（QUICK_CRAFT）。拒绝：抛出与复制、对绑定物品的
     * Shift 点击（在容器界面中会移入容器；在 Wathe 自身界面中会把物品藏进界面不显示的主背包栏位）、创造模式复制拖拽、
     * 拖到外部栏位的拖拽步骤，以及其他任何自身栏位之外的点击（点击窗口外会丢出光标物品）。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        // (a) Number-key/offhand swap of a bound item into a foreign slot. / 数字键或副手交换把绑定物品换入外部栏位。
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceBound()) {
            return true;
        }
        // (b) Nothing bound is involved. / 未涉及绑定物品。
        if (!click.cursorBound() && !click.slotBound()) {
            return false;
        }
        // (c) Throw and creative clone. / 抛出与创造模式复制。
        if (action == SlotActionType.THROW || action == SlotActionType.CLONE) {
            return true;
        }
        // (d) Shift-click of a bound stack; a bound cursor is not moved by it. / 对绑定物品的 Shift 点击；它不会移动光标物品。
        if (action == SlotActionType.QUICK_MOVE) {
            return click.slotBound();
        }
        // (e) Drag: start and end carry no slot; each step adds one slot. / 拖拽：开始与结束不带栏位；每一步加入一个栏位。
        if (action == SlotActionType.QUICK_CRAFT) {
            if (!click.cursorBound()) {
                return false;
            }
            if (quickCraftButton(click.button()) == CLONE_DRAG) {
                return true;
            }
            return click.validSlot() && !click.playerSlot();
        }
        // (f) PICKUP, PICKUP_ALL and SWAP stay inside the holder's own inventory slots. / 其余操作必须留在自身背包栏位。
        return !click.validSlot() || !click.playerSlot();
    }

    /** Vanilla {@code ScreenHandler.unpackQuickCraftButton}. / 原版 {@code ScreenHandler.unpackQuickCraftButton}。 */
    static int quickCraftButton(int button) {
        return button >> 2 & 3;
    }

    /**
     * Side-effect-free facts about one slot click; "bound" means one of the five USEC items.
     * 单次栏位点击的无副作用事实；“绑定”指五种 USEC 物品之一。
     *
     * @param button          the raw click button; for SWAP the other side's PlayerInventory index (40 = offhand), for
     *                        QUICK_CRAFT the packed drag stage and type
     * @param cursorBound     the handler's cursor holds a bound stack
     * @param validSlot       the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotBound       the clicked slot holds a bound stack
     * @param playerSlot      the clicked slot belongs to the clicker's own PlayerInventory (offhand and armor included)
     * @param swapSourceBound for SWAP, the PlayerInventory stack at {@code button} is bound
     */
    record SlotClick(SlotActionType action, int button, boolean cursorBound, boolean validSlot, boolean slotBound,
                     boolean playerSlot, boolean swapSourceBound) {
    }
}
