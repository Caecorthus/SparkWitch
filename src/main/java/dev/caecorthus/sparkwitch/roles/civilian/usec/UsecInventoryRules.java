package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: the five USEC bound items (rifle, magazine, FMJ and AP rounds, suppressor). "Bound" means bound to
 * the holder's life only (owner, 2026-11-05: "AXMC 用不了是个 Bug", "只是死后不掉落罢了"; the BEAR M32A1 answer "直接走
 * wathe 的机制"): any holder may carry and use them, they move like every other item under Wathe's own rules (a live
 * round already refuses Q, the offhand swap, in-screen throws and click-outside drops), no sweep ever strips them from a
 * holder, and they never drop on death: Wathe's death-drop loop skips them ({@code GameFunctionsUsecItemDropMixin}),
 * a vanilla death deletes them ({@code PlayerEntityUsecItemMixin}), and {@code UsecDeathDrops} deletes every copy a
 * victim still carries. Items are identified by class, so every check is safe before registry lookups and on both
 * sides.
 * 稳定契约：五种 USEC 绑定物品（步枪、弹匣、FMJ 与 AP 子弹、消音器）。“绑定”只指与持有者的生命绑定（所有者 2026-11-05：
 * “AXMC 用不了是个 Bug”“只是死后不掉落罢了”；BEAR M32A1 的回答“直接走 wathe 的机制”）：任何持有者都能携带和使用，物品像其他
 * 物品一样遵循 Wathe 自身规则移动（进行中的对局本就拒绝 Q、副手交换、界面内抛出与点击窗口外丢弃），任何清扫都不会从持有者
 * 身上收走它们，且死亡时绝不掉落：Wathe 死亡掉落流程跳过它们（{@code GameFunctionsUsecItemDropMixin}），原版死亡删除它们
 * （{@code PlayerEntityUsecItemMixin}），{@code UsecDeathDrops} 删除死者身上仍携带的所有副本。按物品类识别，因此所有判定在
 * 注册表查询之前、在双端都安全。
 */
public final class UsecInventoryRules {
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
     * Pure rule for the player drop method: only a dead player's drop of a bound item is cancelled (vanilla's inventory
     * drop on death, which Wathe's KEEP_INVENTORY normally skips), so the item vanishes instead of landing. Every drop
     * by a living player follows Wathe's own rules.
     * 玩家丢弃方法的纯规则：只取消已死亡玩家丢出绑定物品（原版死亡时的背包掉落，Wathe 的 KEEP_INVENTORY 通常会跳过它），使物品
     * 消失而不是落地。存活玩家的一切丢弃都遵循 Wathe 自身规则。
     */
    public static boolean vanishesOnDrop(boolean bound, boolean dead) {
        return bound && dead;
    }
}
