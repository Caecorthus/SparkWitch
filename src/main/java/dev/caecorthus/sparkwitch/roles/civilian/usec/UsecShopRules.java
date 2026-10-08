package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.util.ShopEntry;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure USEC shop specification (D11). Wathe builds the list on both sides and buys by index with
 * no version handshake, so the shape is a constant that depends only on the role, never on running state or a runtime
 * probe. Order: empty magazine 25, one .338 FMJ round 50, one .338 AP round 150 (all unlimited), suppressor 50 (stock
 * 1). Types follow precedent: rounds are WEAPON like the Hunter's shells, the magazine and suppressor are TOOL. A bought
 * round first tops up a shown same-item stack ({@link UsecShopPurchase}), then falls back to Wathe's default insertion
 * with SparkFactionAPI's second-row wrapper; the magazine and suppressor (stack size 1) keep the default buy handler.
 * Rounds are plain items and are never auto-loaded (D17).
 * 稳定契约：纯 USEC 商店规格（D11）。Wathe 在两端各自构建列表并按下标购买，没有版本握手，因此列表形状是只取决于职业的
 * 常量，绝不取决于运行状态或运行时探测。顺序：空弹匣 25、单发 .338 FMJ 50、单发 .338 AP 150（均不限量）、消音器 50（限购 1）。
 * 类型沿用先例：子弹与猎人霰弹同为 WEAPON，弹匣与消音器为 TOOL。买到的子弹先补进显示中的同种物品堆（{@link UsecShopPurchase}），
 * 再回退到带 SparkFactionAPI 第二行包装的 Wathe 默认插入；弹匣与消音器（堆叠上限 1）保留默认购买处理。
 * 子弹是普通物品，从不自动装填（D17）。
 */
public final class UsecShopRules {
    /** Wathe's "no stock limit" (the builder's stock is left unset). / Wathe 的“不限库存”（不调用 stock）。 */
    public static final int NO_STOCK_LIMIT = -1;

    public static final EntrySpec MAGAZINE = new EntrySpec(EntryKind.MAGAZINE, UsecRules.MAGAZINE_ENTRY_ID,
            UsecRules.MAGAZINE_PRICE, NO_STOCK_LIMIT, ShopEntry.Type.TOOL);
    public static final EntrySpec FMJ_ROUND = new EntrySpec(EntryKind.FMJ_ROUND, UsecRules.FMJ_ENTRY_ID,
            UsecRules.FMJ_PRICE, NO_STOCK_LIMIT, ShopEntry.Type.WEAPON);
    public static final EntrySpec AP_ROUND = new EntrySpec(EntryKind.AP_ROUND, UsecRules.AP_ENTRY_ID,
            UsecRules.AP_PRICE, NO_STOCK_LIMIT, ShopEntry.Type.WEAPON);
    public static final EntrySpec SUPPRESSOR = new EntrySpec(EntryKind.SUPPRESSOR, UsecRules.SUPPRESSOR_ENTRY_ID,
            UsecRules.SUPPRESSOR_PRICE, UsecRules.SUPPRESSOR_STOCK, ShopEntry.Type.TOOL);

    private static final List<EntrySpec> ENTRIES = List.of(MAGAZINE, FMJ_ROUND, AP_ROUND, SUPPRESSOR);

    private UsecShopRules() {
    }

    /**
     * Role alone decides: Wathe caches stock limits while the round is still STARTING (initializeShopsForPlayers), so a
     * running-state gate would make the suppressor's stock(1) unlimited.
     * 只按职业判定：Wathe 在对局仍处于 STARTING 时（initializeShopsForPlayers）缓存库存上限，若依赖运行状态，
     * 消音器的 stock(1) 会变成不限量。
     */
    public static boolean rebuildsShopFor(@Nullable Role role) {
        return UsecRules.isUsec(role);
    }

    /** Entries in shop order. / 按商店顺序排列的条目。 */
    public static List<EntrySpec> entries() {
        return ENTRIES;
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        MAGAZINE,
        FMJ_ROUND,
        AP_ROUND,
        SUPPRESSOR;

        /**
         * Loose rounds stack, so a purchase tops up an existing stack first; the rest keep the default buy handler.
         * 散装子弹可堆叠，因此购买时先补入已有物品堆；其余条目保留默认购买处理。
         */
        public boolean topsUpStacks() {
            return this == FMJ_ROUND || this == AP_ROUND;
        }
    }

    /**
     * One entry's id, price, stock ({@link #NO_STOCK_LIMIT} = unlimited) and Wathe slot type.
     * 单个条目的 id、价格、库存（{@link #NO_STOCK_LIMIT} 表示不限）与 Wathe 槽位类型。
     */
    public record EntrySpec(EntryKind kind, String id, int price, int stock, ShopEntry.Type type) {
        public boolean hasStockLimit() {
            return stock > 0;
        }
    }
}
