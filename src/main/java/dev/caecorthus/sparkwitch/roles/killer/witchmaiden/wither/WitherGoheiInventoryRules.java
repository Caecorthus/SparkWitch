package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Bound-item rules of the Wither Gohei: it never leaves its holder's own inventory slots, never drops, and never goes
 * to an item frame, armor stand, allay or decorated pot. Clicks follow the strict {@link BoundItemRules} policy, as for
 * the Potion Gunner.
 * 凋灵御币的绑定物品规则：永不离开持有者自身的背包栏位、永不掉落，也不会交给物品展示框、盔甲架、悦灵或饰纹陶罐。
 * 点击与药炮手一样采用 {@link BoundItemRules} 的严格策略。
 */
public final class WitherGoheiInventoryRules {
    private WitherGoheiInventoryRules() {
    }

    public static boolean isGohei(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof WitherGoheiItem;
    }

    /** World-interaction veto for the stack in the used hand. / 针对所用手中物品的世界交互否决。 */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isGohei(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /** Block counterpart of {@link #blocksEntityUse}. / {@link #blocksEntityUse} 的方块版本。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isGohei(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /** Excludes the Gohei from Wathe's death-drop loop. / 将御币排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isGohei(stack);
    }

    /** Refuses a selected-slot drop (Q). / 拒绝丢弃选中的御币（Q）。 */
    public static boolean blocksSelectedDrop(@Nullable PlayerEntity player) {
        return player != null && isGohei(player.getInventory().getMainHandStack());
    }

    /**
     * Server-side slot-click veto through the strict {@link BoundItemRules} policy (Potion Gunner variant): the Gohei
     * stays in the holder's own slots, never by shift-click and never in the offhand (invisible in Wathe's UI; it fires
     * from the main hand only).
     * 通过 {@link BoundItemRules} 严格策略（药炮手版本）进行的服务端栏位点击否决：御币留在持有者自身栏位，不允许 Shift 点击，
     * 也不允许进入副手（副手在 Wathe 界面中不可见，且只从主手射击）。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        return BoundItemRules.blocksStrictSlotClick(player, slotIndex, button, actionType,
                WitherGoheiInventoryRules::isGohei);
    }
}
