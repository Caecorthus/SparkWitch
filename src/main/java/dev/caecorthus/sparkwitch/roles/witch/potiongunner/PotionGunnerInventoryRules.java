package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherItem;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Transfer rules for the bound launcher and the four shells, used by the item-generic binding mixins in
 * {@code mixin/potiongunner/}. Items are identified by class, so these checks stay safe before registry lookups.
 * Clicks follow the strict {@link BoundItemRules} policy, as for the Time Stealer: no creative exemption, so an admin's
 * given launcher or shells stay bound too.
 * 绑定炮筒与四种炮弹的转移规则，供 {@code mixin/potiongunner/} 中的物品通用绑定 mixin 使用。按物品类识别，因此不依赖
 * 注册表获取。点击与窃时者一样采用 {@link BoundItemRules} 的严格策略：没有创造模式豁免，管理员给予的炮筒或炮弹同样受绑定约束。
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

    /**
     * World-interaction veto for the stack in the used hand ({@link BoundItemRules#takesHeldStack}). Without this a
     * launcher would leave the inventory and the 20-tick sweep would mint a fresh one each time, and shells would leave
     * the holder's own slots, breaking their never-in-a-container rule.
     * 针对所用手中物品的世界交互否决（{@link BoundItemRules#takesHeldStack}）。否则炮筒会离开背包，而 20 刻清扫会每次补发
     * 新的炮筒；炮弹也会离开持有者自身栏位，违反“绝不进入容器”的规则。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /**
     * Block counterpart of {@link #blocksEntityUse}, asked by {@code DecoratedPotBlockPotionGunnerItemMixin}.
     * {@link #blocksEntityUse} 的方块版本，由 {@code DecoratedPotBlockPotionGunnerItemMixin} 调用。
     */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
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
     * Server-side slot-click veto for bound items through the strict {@link BoundItemRules} policy (Time Stealer
     * variant). Bound items never leave the holder's own inventory slots; shift-click is always refused (in the holder's
     * own screen it can only push a hotbar stack into the hidden main slots 9-35, where Wathe's hotbar-only screen can
     * no longer reach it); the offhand refuses bound items by either route (button-40 swap, or a click on the offhand
     * slot itself), because it is invisible in Wathe's UI and the launcher fires from the main hand only. Still
     * allowed: PICKUP (left, or right-click half-split) within the holder's own slots — the shell-on-cursor loading
     * click relies on it — PICKUP_ALL on an own slot, taking a bound stack out of the offhand, and hotbar number-key
     * SWAP between own slots.
     * 通过 {@link BoundItemRules} 严格策略（窃时者版本）进行的绑定物品服务端栏位点击否决。绑定物品不得离开持有者自身的背包
     * 栏位；Shift 点击一律拒绝（在自身界面中它只会把快捷栏物品推入隐藏的主背包 9-35 格，Wathe 仅显示快捷栏的界面无法再
     * 触及）；副手通过任一途径（button 40 交换，或直接点击副手栏位）都拒绝绑定物品，因为副手在 Wathe 界面中不可见，且炮筒
     * 只从主手发射。仍然允许：在自身栏位内的 PICKUP（左键或右键半分；光标持炮弹装填的点击依赖于此）、在自身栏位上的
     * PICKUP_ALL、从副手取出绑定物品，以及自身栏位之间的快捷栏数字键 SWAP。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        return BoundItemRules.blocksStrictSlotClick(player, slotIndex, button, actionType,
                PotionGunnerInventoryRules::isBound);
    }
}
