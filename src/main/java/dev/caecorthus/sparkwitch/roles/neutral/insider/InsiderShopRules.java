package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure Insider shop specification (D9, C5). Wathe builds the list on both sides and buys by index
 * with no version handshake, so the shape is a constant that depends only on the exact Insider role, never on running
 * state. Order: revolver (150, stock 1), then crowbar (50, stock 1). The SparkStrength tablet is not sold: SparkStrength
 * issues it free at round start to every police-network role (the Insider included), so this shop never lists it. No
 * {@code wathe:grenade}, so SparkStrength never appends its M67.
 * 稳定契约：纯内应商店规格（D9、C5）。Wathe 在两端各自构建列表并按下标购买，没有版本握手，因此列表形状是只取决于
 * 精确内应职业的常量，绝不取决于运行状态。顺序：左轮（150，库存 1），然后是撬棍（50，库存 1）。不出售 SparkStrength
 * 平板：SparkStrength 在开局免费发放给所有警察网络职业（含内应），因此本商店从不列出平板。不出售 {@code wathe:grenade}，
 * 因此 SparkStrength 不会追加 M67。
 */
public final class InsiderShopRules {
    /** Every Insider entry is limited to one per round (D9 for the revolver). / 每个内应条目每局限购一件（左轮见 D9）。 */
    public static final int ENTRY_STOCK = 1;

    public static final EntrySpec REVOLVER = new EntrySpec(EntryKind.REVOLVER, InsiderRules.REVOLVER_ENTRY_ID,
            InsiderRules.REVOLVER_PRICE, ENTRY_STOCK);
    public static final EntrySpec CROWBAR = new EntrySpec(EntryKind.CROWBAR, InsiderRules.CROWBAR_ENTRY_ID,
            InsiderRules.CROWBAR_PRICE, ENTRY_STOCK);

    private static final List<EntrySpec> ENTRIES = List.of(REVOLVER, CROWBAR);

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

    /** Entries in shop order. / 按商店顺序排列的条目。 */
    public static List<EntrySpec> entries() {
        return ENTRIES;
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        REVOLVER,
        CROWBAR
    }

    /** One entry's id, price and stock. / 单个条目的 id、价格与库存。 */
    public record EntrySpec(EntryKind kind, String id, int price, int stock) {
    }
}
