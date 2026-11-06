package dev.caecorthus.sparkwitch.roles.killer.natsume;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure 夏目安安 shop plan over the exact plain Wathe {@code KillerShopBuilder} entry ids (no substring matching, so
 * SparkTraits, SparkStrength and other mods' rows are never touched by accident). Seven native rows are removed and the
 * poison vial is repriced to 25 coins; every other row keeps its exact price, stock, cooldown and callback. Removing
 * {@code grenade} also removes SparkStrength's M67: SparkStrength appends it after all listeners only while a grenade
 * row is still sellable (owner: the M67 is not kept). {@code blackout} is dead content (SparkStrength strips it
 * globally and other SparkWitch killer shops filter it too), removed here explicitly. The rewrite depends only on the
 * ids, because Wathe builds the list on both sides and resolves a purchase by index.
 * 夏目安安商店的纯规划，只匹配 Wathe {@code KillerShopBuilder} 的精确原生商品 id（不做子串匹配，因此绝不会误伤 SparkTraits、
 * SparkStrength 或其他模组的条目）。移除七个原生条目，并把毒药瓶改价为 25 金币；其余条目保持原有价格、库存、冷却与回调。
 * 移除 {@code grenade} 也会移除 SparkStrength 的 M67：SparkStrength 只在手雷条目仍可购买时才在所有监听器之后追加它（所有者：
 * 不保留 M67）。{@code blackout} 是失效内容（SparkStrength 全局移除，其他 SparkWitch 杀手商店也会过滤），此处显式移除。改写只取决于
 * id，因为 Wathe 在两端构建列表并按下标解析购买。
 */
public final class NatsumeShopRules {
    /** Plain Wathe ids 夏目安安 never sells. / 夏目安安从不出售的 Wathe 原生 id。 */
    static final Set<String> REMOVED_ENTRY_IDS = Set.of(
            "grenade",
            "psycho_mode",
            "scorpion",
            "note",
            "crowbar",
            "firecracker",
            "blackout"
    );
    /** Wathe's poison vial row. / Wathe 的毒药瓶条目。 */
    static final String POISON_VIAL_ENTRY_ID = "poison_vial";
    /** Owner price for her poison vial. / 所有者定下的毒药瓶价格。 */
    static final int POISON_VIAL_PRICE = 25;

    private NatsumeShopRules() {
    }

    /** Whether the row with this id is removed. / 该 id 的条目是否被移除。 */
    static boolean removes(@Nullable String entryId) {
        return entryId != null && REMOVED_ENTRY_IDS.contains(entryId);
    }

    /**
     * The price this row should have, or {@code price} unchanged: only the poison vial is repriced.
     * 该条目应有的价格；只有毒药瓶改价，其余原样返回 {@code price}。
     */
    static int priceFor(@Nullable String entryId, int price) {
        return POISON_VIAL_ENTRY_ID.equals(entryId) ? POISON_VIAL_PRICE : price;
    }

    /** Ids kept, in order, for a list of entry ids (tests and documentation). / 保留下来的 id（按原顺序）。 */
    static List<String> keptIds(List<String> entryIds) {
        return entryIds.stream().filter(id -> !removes(id)).toList();
    }
}
