package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun.ShriekGunItem;
import dev.doctor4t.wathe.api.Role;
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
 * Binding rules for the Shriek Gun, used by the role-owned guard mixins in {@code mixin/abysslistener/}: the gun never
 * becomes an item entity, never leaves its holder's own inventory slots, and never drops on death. The gun is
 * identified by class, so the checks stay safe before registry lookups. Copied from, never shared with, the Time
 * Stealer rules (house convention: duplicate bound-item rules per role). There is no creative exemption.
 * 啸音铳的绑定规则，供 {@code mixin/abysslistener/} 中本职业自有的防护 mixin 使用：枪永远不会变成物品实体、
 * 不会离开持有者自身的背包栏位、死亡时也不会掉落。按物品类识别，因此不依赖注册表获取。
 * 复制而非共享窃时者的规则（约定：绑定物品规则按职业各自复制）。没有创造模式豁免。
 */
public final class AbyssListenerInventoryRules {
    private AbyssListenerInventoryRules() {
    }

    public static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ShriekGunItem;
    }

    /** Refuses every drop of the gun into an item entity (Q, Ctrl+Q, any server drop path). / 拒绝把枪丢成掉落物。 */
    public static boolean blocksDrop(@Nullable ItemStack stack) {
        return isGun(stack);
    }

    /** Excludes the gun from Wathe's death-drop loop. / 将枪排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isGun(stack);
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack, and a decorated pot inserts any held item. Without this the gun would leave the
     * inventory, show in the world although it is hidden in hand, and the 20-tick sweep would mint a fresh one each
     * time. Copied from, never shared with, the Time Stealer rules.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品，饰纹陶罐会放入任意手持物品。
     * 否则枪会离开背包、在世界中显露（尽管手持时对他人隐藏），而 20 tick 清理会每次补发一把新枪。复制而非共享窃时者的规则。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isGun(held) && target != null && takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockAbyssListenerGunMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockAbyssListenerGunMixin} 调用。
     */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isGun(held) && target != null && takesHeldStack(target.getBlock().getClass());
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
     * Holder entitlement: playing (round running, has a role), alive, and exactly the Abyss Listener. Every other
     * player, including a former Abyss Listener after a role change or Wraith transition, loses the gun.
     * 持有资格：正在对局中（回合进行、有身份）、存活，且身份恰好是聆渊者。其他所有玩家（包括换职业或转为亡灵的前聆渊者）都会失去枪。
     */
    public static boolean isEntitled(boolean playingAndAlive, @Nullable Role role) {
        return playingAndAlive && AbyssListenerRules.isAbyssListener(role);
    }

    /**
     * Round-start grant gate. Wathe fires ON_FINISH_INITIALIZE while the round is still STARTING (not yet "running"),
     * so the role map and the dead set are read directly instead of {@link #isEntitled}.
     * 开局发放门槛。Wathe 在回合仍处于 STARTING（尚未“进行中”）时触发 ON_FINISH_INITIALIZE，
     * 因此直接读取身份表与死亡集合，而不使用 {@link #isEntitled}。
     */
    public static boolean receivesRoundStartGun(boolean hasRole, boolean dead, @Nullable Role role) {
        return hasRole && !dead && AbyssListenerRules.isAbyssListener(role);
    }

    /**
     * Server-side slot-click veto for the gun. Gathers side-effect-free facts from the live handler and decides through
     * the pure {@link #blocksSlotClick(SlotClick)} core.
     * 枪的服务端栏位点击否决。从当前界面收集无副作用的事实，再交由纯函数 {@link #blocksSlotClick(SlotClick)} 判定。
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
        boolean swapSourceGun = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isGun(inventory.getStack(button));
        return blocksSlotClick(new SlotClick(
                actionType,
                button,
                isGun(handler.getCursorStack()),
                validSlot,
                clicked != null && isGun(clicked.getStack()),
                clicked != null && clicked.inventory == inventory,
                swapSourceGun,
                // The offhand is also reachable as a handler slot (PlayerScreenHandler index 45 → inventory index 40).
                // 副手也可作为界面栏位被直接点击（PlayerScreenHandler 下标 45 → 背包下标 40）。
                clicked != null && clicked.inventory == inventory
                        && clicked.getIndex() == PlayerInventory.OFF_HAND_SLOT));
    }

    /**
     * Pure decision, checked in order (the Time Stealer matrix): throw and clone are refused; shift-click of the gun is
     * always refused (in the holder's own screen it can only push it into the hidden main slots 9-35); drag
     * distribution with the gun on the cursor is refused; any offhand swap or placement involving the gun is refused
     * (the offhand is invisible in Wathe's UI); everything else must stay inside the holder's own inventory slots, so
     * nothing moves into a container or outside the window. Still allowed: PICKUP within the holder's own slots,
     * PICKUP_ALL on an own slot, hotbar number-key SWAP between own slots, and taking the gun out of the offhand.
     * 纯函数判定，按顺序检查（窃时者矩阵）：拒绝抛出与复制；枪的 Shift 点击一律拒绝（在自身界面中只会把它推入隐藏的
     * 主背包 9-35 格）；光标持枪时拒绝拖拽分配；涉及枪的副手交换或放置一律拒绝（副手在 Wathe 界面中不可见）；
     * 其余操作必须留在持有者自身背包栏位内，因此不会移入容器或窗口外。仍然允许：在自身栏位内 PICKUP、在自身栏位上
     * PICKUP_ALL、自身栏位之间的快捷栏数字键 SWAP，以及从副手取出枪。
     */
    static boolean blocksSlotClick(SlotClick click) {
        SlotActionType action = click.action();
        // (a) Number-key/offhand swap of the gun into a foreign slot. / 数字键或副手交换把枪换入外部栏位。
        if (action == SlotActionType.SWAP && !click.playerSlot() && click.swapSourceGun()) {
            return true;
        }
        // (b) Offhand swap involving the gun, even between own slots. / 涉及枪的副手交换，即使在自身栏位之间。
        if (action == SlotActionType.SWAP
                && (click.button() == PlayerInventory.OFF_HAND_SLOT || click.offhandSlot())
                && (click.slotGun() || click.swapSourceGun())) {
            return true;
        }
        // (c) The gun is not involved. / 未涉及枪。
        if (!click.cursorGun() && !click.slotGun()) {
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
        // (f) Drag distribution with the gun on the cursor. / 光标持枪时的拖拽分配。
        if (action == SlotActionType.QUICK_CRAFT && click.cursorGun()) {
            return true;
        }
        // (f2) Placing the gun onto the offhand slot. / 把光标上的枪放入副手栏位。
        if (action == SlotActionType.PICKUP && click.cursorGun() && click.offhandSlot()) {
            return true;
        }
        // (g) Anything else must stay inside the holder's own inventory slots. / 其余操作必须留在持有者自身背包栏位。
        return !click.validSlot() || !click.playerSlot();
    }

    /**
     * Side-effect-free facts about one slot click. / 单次栏位点击的无副作用事实。
     *
     * @param button        the raw click button; for SWAP the other side's PlayerInventory index (40 = offhand)
     * @param cursorGun     the handler's cursor holds the gun
     * @param validSlot     the slot index addresses a real handler slot (not -999 or another sentinel)
     * @param slotGun       the clicked slot holds the gun
     * @param playerSlot    the clicked slot belongs to the clicker's own PlayerInventory
     * @param swapSourceGun for SWAP, the PlayerInventory stack at {@code button} is the gun
     * @param offhandSlot   the clicked slot is the clicker's own offhand (PlayerInventory index 40)
     */
    record SlotClick(SlotActionType action, int button, boolean cursorGun, boolean validSlot, boolean slotGun,
                     boolean playerSlot, boolean swapSourceGun, boolean offhandSlot) {
    }
}
