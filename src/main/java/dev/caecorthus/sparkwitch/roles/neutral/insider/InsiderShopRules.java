package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure Insider shop specification (D4, D9, C5). Wathe builds the list on both sides and buys by
 * index with no version handshake, so the shape depends only on the exact Insider role and on whether the
 * SparkStrength tablet item is registered (the same mod set on client and server), never on running state. Order:
 * revolver (150, stock 1), crowbar (50, stock 1), then the SparkStrength tablet (100, stock 1) under SparkStrength's
 * own entry id, so SparkStrength's final append skips its own tablet and its "already owned" deny still applies. No
 * {@code wathe:grenade}, so SparkStrength never appends its M67.
 * 稳定契约：纯内应商店规格（D4、D9、C5）。Wathe 在两端各自构建列表并按下标购买，没有版本握手，因此列表形状只取决于
 * 精确的内应职业以及 SparkStrength 平板物品是否已注册（两端模组集合相同），绝不取决于运行状态。顺序：左轮（150，库存 1）、
 * 撬棍（50，库存 1），然后是 SparkStrength 平板（100，库存 1），使用 SparkStrength 自己的条目 id，
 * 这样 SparkStrength 的末尾追加会跳过其自身平板，而其“已拥有”拒绝仍然生效。不出售 {@code wathe:grenade}，
 * 因此 SparkStrength 不会追加 M67。
 */
public final class InsiderShopRules {
    /** Every Insider entry is limited to one per round (D9 for the revolver). / 每个内应条目每局限购一件（左轮见 D9）。 */
    public static final int ENTRY_STOCK = 1;
    public static final String TABLET_ENTRY_ID = SparkStrengthTabletCompat.SS_TABLET_ENTRY_ID;
    /** Shop description colour, the same grey as other SparkWitch shop lore. / 商店描述颜色，与其他 SparkWitch 商店一致的灰色。 */
    public static final int DESCRIPTION_COLOR = 0x808080;
    public static final String TABLET_DESCRIPTION_KEY = "shop.sparkwitch.insider_tablet.description";

    public static final EntrySpec REVOLVER = new EntrySpec(EntryKind.REVOLVER, InsiderRules.REVOLVER_ENTRY_ID,
            InsiderRules.REVOLVER_PRICE, ENTRY_STOCK);
    public static final EntrySpec CROWBAR = new EntrySpec(EntryKind.CROWBAR, InsiderRules.CROWBAR_ENTRY_ID,
            InsiderRules.CROWBAR_PRICE, ENTRY_STOCK);
    public static final EntrySpec TABLET = new EntrySpec(EntryKind.TABLET, TABLET_ENTRY_ID,
            InsiderRules.TABLET_PRICE, ENTRY_STOCK);

    private static final List<EntrySpec> WITH_TABLET = List.of(REVOLVER, CROWBAR, TABLET);
    private static final List<EntrySpec> WITHOUT_TABLET = List.of(REVOLVER, CROWBAR);

    private InsiderShopRules() {
    }

    /**
     * The exact Insider role alone decides: Wathe caches stock limits while the round is still STARTING, so a
     * running-state gate would turn stock(1) into an unlimited entry. The role-only shop also keeps Wathe's
     * {@code canAccessShop} fallback true while STARTING.
     * 只按精确的内应职业判定：Wathe 在对局仍处于 STARTING 时缓存库存上限，依赖运行状态会让 stock(1) 变成不限量；
     * 只按职业建店也让 Wathe 的 {@code canAccessShop} 兜底在 STARTING 阶段保持为真。
     */
    public static boolean rebuildsShopFor(@Nullable Role role) {
        return InsiderParticipation.isInsiderRole(role);
    }

    /**
     * Entries in shop order. Without SparkStrength there is no tablet entry (D4).
     * 按商店顺序排列的条目。没有 SparkStrength 时不出现平板条目（D4）。
     */
    public static List<EntrySpec> entries(boolean tabletItemPresent) {
        return tabletItemPresent ? WITH_TABLET : WITHOUT_TABLET;
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        REVOLVER,
        CROWBAR,
        TABLET
    }

    /** One entry's id, price and stock. / 单个条目的 id、价格与库存。 */
    public record EntrySpec(EntryKind kind, String id, int price, int stock) {
    }
}
