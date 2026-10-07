package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

import java.util.List;

/**
 * Owned by WP1: replaces the USEC shop on both sides with {@link UsecShopRules#entries()} (magazine, FMJ, AP,
 * suppressor). Called once from {@link UsecFeatureService#register()}. SparkTraits entries (e.g. the Impostor
 * revolver) survive the rebuild, every entry passes through the optional SparkTraits Charisma discount, and every
 * entry keeps Wathe's default buy handler. The SparkStrength tablet is issued free at round start, never sold here.
 * 归 WP1 所有：在两端把 USEC 商店替换为 {@link UsecShopRules#entries()}（弹匣、FMJ、AP、消音器）。由
 * {@link UsecFeatureService#register()} 调用一次。SparkTraits 条目（如内鬼左轮）在重建后保留，每个条目经过可选的
 * SparkTraits 魅力折扣，并保留 Wathe 默认购买处理。SparkStrength 平板开局免费发放，此处不出售。
 */
public final class UsecShopService {
    private static boolean registered;

    private UsecShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(UsecShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!UsecShopRules.rebuildsShopFor(role)) {
            return;
        }
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (UsecShopRules.EntrySpec spec : UsecShopRules.entries()) {
            ShopEntry entry = entry(spec);
            context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, entry));
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    /**
     * Default buy handler only: never Wathe's hotbar-only {@code insertStackInFreeSlot} directly, so SparkFactionAPI's
     * {@code onBuy} wrapper can fall back to the second row. A magazine is sold empty (no CUSTOM_DATA).
     * 只使用默认购买处理：从不直接调用 Wathe 只放快捷栏的 {@code insertStackInFreeSlot}，使 SparkFactionAPI 对
     * {@code onBuy} 的包装能回退到第二行。弹匣以空弹匣出售（无 CUSTOM_DATA）。
     */
    private static ShopEntry entry(UsecShopRules.EntrySpec spec) {
        ShopEntry.Builder builder = new ShopEntry.Builder(spec.id(), item(spec.kind()).getDefaultStack(), spec.price(),
                spec.type());
        if (spec.hasStockLimit()) {
            builder.stock(spec.stock());
        }
        return builder.build();
    }

    private static Item item(UsecShopRules.EntryKind kind) {
        return switch (kind) {
            case MAGAZINE -> SparkWitchItems.usecMagazine();
            case FMJ_ROUND -> SparkWitchItems.usecFmjRound();
            case AP_ROUND -> SparkWitchItems.usecApRound();
            case SUPPRESSOR -> SparkWitchItems.usecSuppressor();
        };
    }
}
