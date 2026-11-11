package dev.caecorthus.sparkwitch.roles.killer.hiro.shop;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.item.rope.RopeRules;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.List;
import net.minecraft.util.Identifier;

/**
 * 二阶堂希罗's shop plan (owner spec): a full replacement of the killer shop with exactly five rows, using the
 * Murderous Witch shop's entry ids, types and prices for the four shared rows. {@code maxStock <= 0} means unlimited.
 * Wathe builds the list on both sides and resolves a purchase by index, so the plan must not depend on side.
 * 二阶堂希罗的商店规划（所有者规格）：完全替换杀手商店，恰好五个条目；四个共有条目沿用杀意魔女商店的条目 id、类型与价格。
 * {@code maxStock <= 0} 表示不限购。Wathe 在两端构建列表并按下标解析购买，因此规划不得依赖运行端。
 */
public final class HiroShopRules {
    private static final Identifier KNIFE_ID = Identifier.of("wathe", "knife");
    private static final Identifier LOCKPICK_ID = Identifier.of("wathe", "lockpick");
    private static final Identifier CROWBAR_ID = Identifier.of("wathe", "crowbar");
    private static final Identifier FIRE_POKER_ID = SparkWitch.id("fire_poker");

    private HiroShopRules() {
    }

    /** The five rows in shop order. / 按商店顺序排列的五个条目。 */
    public static List<PlannedEntry> plannedEntries() {
        return List.of(
                new PlannedEntry("knife", KNIFE_ID, 100, ShopEntry.Type.WEAPON, 1),
                new PlannedEntry("lockpick", LOCKPICK_ID, 50, ShopEntry.Type.TOOL, 1),
                new PlannedEntry("crowbar", CROWBAR_ID, 25, ShopEntry.Type.TOOL, 1),
                new PlannedEntry("fire_poker", FIRE_POKER_ID, 50, ShopEntry.Type.WEAPON, 1),
                // Unlimited: a rope is spent by every successful strangle. / 不限购：每次勒杀成功都会消耗一根绳索。
                new PlannedEntry("rope", RopeRules.ITEM_ID, RopeRules.PRICE, ShopEntry.Type.WEAPON, -1)
        );
    }

    /** One planned row: entry id, item registry id, price, frame type and stock ({@code <= 0}: unlimited). */
    public record PlannedEntry(String id, Identifier item, int price, ShopEntry.Type type, int maxStock) {
    }
}
