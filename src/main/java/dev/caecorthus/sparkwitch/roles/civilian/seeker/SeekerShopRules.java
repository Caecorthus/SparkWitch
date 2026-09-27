package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the pure Seeker shop specification. Wathe builds the list on both sides and buys by index with no
 * version handshake, so the shape depends only on the role and on whether the SparkStrength tablet item is registered
 * (the same mod set on client and server) — never on a runtime API probe. Order: camera (150, no stock limit; a
 * duplicate is denied at purchase time), then the SparkStrength tablet (50, stock 1) under SparkStrength's own entry
 * id, so SparkStrength's final append skips its 150 tablet and its "already owned" deny still applies.
 * 稳定契约：纯搜寻者商店规格。Wathe 在两端各自构建列表并按下标购买，没有版本握手，因此列表形状只取决于职业
 * 以及 SparkStrength 平板物品是否已注册（两端模组集合相同），绝不取决于运行时 API 探测。顺序：摄像头（150，不限库存；
 * 重复购买在购买时拒绝），然后是 SparkStrength 平板（50，库存 1），使用 SparkStrength 自己的条目 id，
 * 这样 SparkStrength 的末尾追加会跳过其 150 的平板条目，而其“已拥有”拒绝仍然生效。
 */
public final class SeekerShopRules {
    /** Wathe's "no stock limit" (the builder's stock is left unset). / Wathe 的“不限库存”（不调用 stock）。 */
    public static final int NO_STOCK_LIMIT = -1;
    public static final int TABLET_STOCK = 1;
    /** Shop description colour, the same grey as other SparkWitch shop lore. / 商店描述颜色，与其他 SparkWitch 商店一致的灰色。 */
    public static final int DESCRIPTION_COLOR = 0x808080;
    /** Purchase-deny reason shown by Wathe (translation key). / Wathe 显示的拒绝原因（翻译键）。 */
    public static final String CAMERA_ALREADY_OWNED_KEY = "message.sparkwitch.seeker.camera.already_owned";
    public static final String TABLET_DESCRIPTION_KEY = "shop.sparkwitch.seeker_tablet.description";

    public static final EntrySpec CAMERA = new EntrySpec(EntryKind.CAMERA, SeekerRules.CAMERA_ENTRY_ID,
            SeekerRules.CAMERA_PRICE, NO_STOCK_LIMIT);
    public static final EntrySpec TABLET = new EntrySpec(EntryKind.TABLET, SeekerRules.TABLET_ENTRY_ID,
            SeekerRules.TABLET_PRICE, TABLET_STOCK);

    private static final List<EntrySpec> WITH_TABLET = List.of(CAMERA, TABLET);
    private static final List<EntrySpec> WITHOUT_TABLET = List.of(CAMERA);

    private SeekerShopRules() {
    }

    /**
     * Role alone decides: Wathe caches stock limits while the round is still STARTING, so a running-state gate would
     * turn the tablet's stock(1) into an unlimited entry.
     * 只按职业判定：Wathe 在对局仍处于 STARTING 时缓存库存上限，依赖运行状态会让平板的 stock(1) 变成不限量。
     */
    public static boolean rebuildsShopFor(@Nullable Role role) {
        return SeekerRules.isSeeker(role);
    }

    /**
     * Entries in shop order. Without SparkStrength (forced Seeker, Q7) there is no tablet entry.
     * 按商店顺序排列的条目。没有 SparkStrength（强制指定的搜寻者，Q7）时没有平板条目。
     */
    public static List<EntrySpec> entries(boolean tabletItemPresent) {
        return tabletItemPresent ? WITH_TABLET : WITHOUT_TABLET;
    }

    public static boolean isCameraEntry(@Nullable String entryId) {
        return CAMERA.id().equals(entryId);
    }

    /**
     * At most one camera per owner (Q8): deny while one is held (anywhere, including the cursor) or placed; it is
     * re-buyable once the placed camera is destroyed.
     * 每名拥有者最多一台摄像头（Q8）：持有（任意位置，含光标）或已放置时拒绝；已放置的摄像头被毁后可再次购买。
     */
    public static boolean deniesCameraPurchase(@Nullable String entryId, boolean holdsCamera, boolean cameraPlaced) {
        return isCameraEntry(entryId) && (holdsCamera || cameraPlaced);
    }

    /** Which item an entry sells. / 条目出售的物品。 */
    public enum EntryKind {
        CAMERA,
        TABLET
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
