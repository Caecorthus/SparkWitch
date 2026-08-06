package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.util.ShopEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Preserves optional SparkTraits shop entries around SparkWitch exclusive shop rebuilds.
 * 在 SparkWitch 专属商店清空重建前后，保留 SparkTraits 词条系统追加的商店条目。
 */
public final class SparkTraitsShopEntryPreserver {
    private static final String SPARKTRAITS_SHOP_ID_PREFIX = "sparktraits:";

    private SparkTraitsShopEntryPreserver() {
    }

    /**
     * Captures all SparkTraits-owned entries that already exist in the current shop context.
     * 捕获当前商店中已经由 SparkTraits 追加的所有条目；只按 id 命名空间判断，避免依赖 SparkTraits 类。
     */
    public static List<ShopEntry> capture(BuildShopEntries.ShopContext context) {
        List<ShopEntry> preservedEntries = new ArrayList<>();
        for (ShopEntry entry : context.getEntries()) {
            if (isSparkTraitsEntry(entry)) {
                preservedEntries.add(entry);
            }
        }
        return preservedEntries;
    }

    /**
     * Restores captured SparkTraits entries after SparkWitch has rebuilt its role-owned shop.
     * 在 SparkWitch 重建职业专属商店后恢复词条商品，保证内鬼左轮以及未来 sparktraits:* 商品不会被 clearEntries 吞掉。
     */
    public static void restore(BuildShopEntries.ShopContext context, List<ShopEntry> preservedEntries) {
        if (preservedEntries == null || preservedEntries.isEmpty()) {
            return;
        }
        for (ShopEntry entry : preservedEntries) {
            String entryId = entry.id();
            if (entryId != null && !containsEntryId(context, entryId)) {
                context.addEntry(entry);
            }
        }
    }

    private static boolean isSparkTraitsEntry(ShopEntry entry) {
        String entryId = entry.id();
        return entryId != null && entryId.startsWith(SPARKTRAITS_SHOP_ID_PREFIX);
    }

    private static boolean containsEntryId(BuildShopEntries.ShopContext context, String entryId) {
        for (ShopEntry entry : context.getEntries()) {
            if (entry.id().equals(entryId)) {
                return true;
            }
        }
        return false;
    }
}
