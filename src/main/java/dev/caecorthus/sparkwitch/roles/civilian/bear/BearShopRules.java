package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.util.ShopEntry;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure BEAR shop specification (owner 2026-11-05, D9). Wathe builds the list on both sides and
 * buys by index with no handshake, so the shape depends only on the role, never on running state. Order (owner's):
 * Rys-T helmet 100 (stock 1), CENS ProFlex DX5 earplugs 50 (stock 1), 40 mm smoke round 75 (unlimited). Types follow
 * precedent: gear is TOOL, the round is WEAPON like the USEC and Hunter rounds. Landing (D9): a bought helmet goes on
 * the head when the head slot is empty and bought earplugs into the headset slot when it is empty, else Wathe's default
 * insertion (SparkFactionAPI second row included); a round first tops up a shown stack. A second helmet or pair of
 * earplugs is refused while the buyer already owns one (stock is not enforced for a role gained mid-round).
 * 稳定契约：纯 BEAR 商店规格（所有者 2026-11-05，D9）。Wathe 在两端构建列表并按下标购买、没有握手，因此列表形状只取决于职业，
 * 绝不取决于运行状态。顺序（按所有者给出）：Rys-T 头盔 100（限购 1）、CENS ProFlex DX5 耳塞 50（限购 1）、40 mm 烟雾榴弹 75
 * （不限量）。类型沿用先例：装备为 TOOL，榴弹与 USEC、猎人的弹药同为 WEAPON。落点（D9）：头部槽为空时买到的头盔直接戴上，
 * 耳机槽为空时买到的耳塞直接戴上，否则走 Wathe 默认放入（含 SparkFactionAPI 第二行）；榴弹先补进显示中的物品堆。买家已持有
 * 头盔或耳塞时拒绝再买（中途获得的职业不受库存限制）。
 */
public final class BearShopRules {
    /** Wathe's "no stock limit" (the builder's stock is left unset). / Wathe 的“不限库存”（不调用 stock）。 */
    public static final int NO_STOCK_LIMIT = -1;

    public static final EntrySpec RYS_T = new EntrySpec(EntryKind.RYS_T, BearRules.RYS_T_ENTRY_ID,
            BearRules.PRICE_RYS_T, BearRules.RYS_T_STOCK, ShopEntry.Type.TOOL);
    public static final EntrySpec EARPLUGS = new EntrySpec(EntryKind.EARPLUGS, BearRules.EARPLUGS_ENTRY_ID,
            BearRules.PRICE_EARPLUGS, BearRules.EARPLUGS_STOCK, ShopEntry.Type.TOOL);
    public static final EntrySpec SMOKE_ROUND = new EntrySpec(EntryKind.SMOKE_ROUND, BearRules.SMOKE_ROUND_ENTRY_ID,
            BearRules.PRICE_SMOKE_ROUND, NO_STOCK_LIMIT, ShopEntry.Type.WEAPON);

    private static final List<EntrySpec> ENTRIES = List.of(RYS_T, EARPLUGS, SMOKE_ROUND);

    /** Lang key of the "already owned" refusal. / “已持有”拒绝提示的语言键。 */
    public static final String ALREADY_OWNED_KEY = "shop.error.sparkwitch.bear_owned";

    private BearShopRules() {
    }

    /**
     * Role alone decides: Wathe caches stock limits while the round is still STARTING, so a running-state gate would
     * make the stock-1 entries unlimited. / 只按职业判定：Wathe 在对局仍处于 STARTING 时缓存库存上限，若依赖运行状态，
     * 限购 1 的条目会变成不限量。
     */
    public static boolean rebuildsShopFor(@Nullable Role role) {
        return BearRules.isBear(role);
    }

    public static List<EntrySpec> entries() {
        return ENTRIES;
    }

    /** The spec with {@code entryId}, or null. / id 为 {@code entryId} 的规格；没有则为 null。 */
    public static @Nullable EntrySpec byId(@Nullable String entryId) {
        for (EntrySpec spec : ENTRIES) {
            if (spec.id().equals(entryId)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * Gear the buyer may own only one of is refused while one is owned. / 只能持有一件的装备在已持有时拒绝购买。
     */
    public static boolean refusesPurchase(@Nullable EntryKind kind, boolean ownsOne) {
        return kind != null && kind.oneOnly() && ownsOne;
    }

    /**
     * D9: the bought gear goes straight into its worn slot (head or headset) when that slot is empty; otherwise, and
     * for the round, the default insertion runs. / D9：所购装备的穿戴槽（头部或耳机槽）为空时直接戴上；否则（以及榴弹）走默认放入。
     */
    public static boolean landsInWornSlot(@Nullable EntryKind kind, boolean wornSlotEmpty) {
        return kind != null && kind.oneOnly() && wornSlotEmpty;
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        RYS_T,
        EARPLUGS,
        SMOKE_ROUND;

        /** One per buyer: the helmet and the earplugs. / 每人限一件：头盔与耳塞。 */
        public boolean oneOnly() {
            return this == RYS_T || this == EARPLUGS;
        }
    }

    /** One entry's id, price, stock ({@link #NO_STOCK_LIMIT} = unlimited) and Wathe slot type. / 条目规格。 */
    public record EntrySpec(EntryKind kind, String id, int price, int stock, ShopEntry.Type type) {
        public boolean hasStockLimit() {
            return stock > 0;
        }
    }
}
