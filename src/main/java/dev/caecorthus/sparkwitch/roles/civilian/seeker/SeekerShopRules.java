package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure Seeker shop specification. Wathe builds the list on both sides and buys by index with no
 * version handshake, so the shape is a constant that depends only on the role — never on a runtime API probe. It holds
 * one entry: the camera (150, no stock limit and re-buyable any time; the owner may own any number, only money limits
 * it). The SparkStrength tablet is not sold: SparkStrength issues it free at round start to every police-network role
 * (the Seeker included), so this shop never lists it.
 * 稳定契约：纯搜寻者商店规格。Wathe 在两端各自构建列表并按下标购买，没有版本握手，因此列表形状是只取决于职业的常量，
 * 绝不取决于运行时 API 探测。只有一个条目：摄像头（150，不限库存，随时可再买；拥有者可拥有任意数量，只受金钱限制）。
 * 不出售 SparkStrength 平板：SparkStrength 在开局免费发放给所有警察网络职业（含搜寻者），因此本商店从不列出平板。
 */
public final class SeekerShopRules {
    /** Wathe's "no stock limit" (the builder's stock is left unset). / Wathe 的“不限库存”（不调用 stock）。 */
    public static final int NO_STOCK_LIMIT = -1;

    public static final EntrySpec CAMERA = new EntrySpec(EntryKind.CAMERA, SeekerRules.CAMERA_ENTRY_ID,
            SeekerRules.CAMERA_PRICE, NO_STOCK_LIMIT);

    private static final List<EntrySpec> ENTRIES = List.of(CAMERA);

    private SeekerShopRules() {
    }

    /**
     * Role alone decides, never running state: Wathe builds the list on both sides, including while the round is still
     * STARTING.
     * 只按职业判定，绝不依赖运行状态：Wathe 在两端构建列表，包括对局仍处于 STARTING 时。
     */
    public static boolean rebuildsShopFor(@Nullable Role role) {
        return SeekerRules.isSeeker(role);
    }

    /** Entries in shop order. / 按商店顺序排列的条目。 */
    public static List<EntrySpec> entries() {
        return ENTRIES;
    }

    public static boolean isCameraEntry(@Nullable String entryId) {
        return CAMERA.id().equals(entryId);
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        CAMERA
    }

    /**
     * One entry's id, price and stock ({@link #NO_STOCK_LIMIT} = unlimited).
     * 单个条目的 id、价格与库存（{@link #NO_STOCK_LIMIT} 表示不限）。
     */
    public record EntrySpec(EntryKind kind, String id, int price, int stock) {
        public boolean hasStockLimit() {
            return stock > 0;
        }
    }
}
