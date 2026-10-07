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
 * revolver) survive the rebuild and every entry passes through the optional SparkTraits Charisma discount. Rounds top up
 * a shown stack before falling back to Wathe's default insertion ({@link UsecShopPurchase}); the magazine and suppressor
 * keep the default buy handler. The SparkStrength tablet is issued free at round start, never sold here.
 * 归 WP1 所有：在两端把 USEC 商店替换为 {@link UsecShopRules#entries()}（弹匣、FMJ、AP、消音器）。由
 * {@link UsecFeatureService#register()} 调用一次。SparkTraits 条目（如内鬼左轮）在重建后保留，每个条目经过可选的
 * SparkTraits 魅力折扣。子弹先补进显示中的物品堆，再回退到 Wathe 默认插入（{@link UsecShopPurchase}）；弹匣与消音器保留默认
 * 购买处理。SparkStrength 平板开局免费发放，此处不出售。
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
     * Never calls Wathe's hotbar-only {@code insertStackInFreeSlot} directly, so SparkFactionAPI's {@code onBuy} wrapper
     * can fall back to the second row. A round's handler tops up a shown stack, else runs the handler-less twin's
     * default {@code onBuy}; both entries share id, stack, price, type and stock, so the list shape is unchanged.
     * A magazine is sold empty (no CUSTOM_DATA).
     * 从不直接调用 Wathe 只放快捷栏的 {@code insertStackInFreeSlot}，使 SparkFactionAPI 对 {@code onBuy} 的包装能回退到
     * 第二行。子弹的处理器先补进显示中的物品堆，否则执行不带处理器的孪生条目的默认 {@code onBuy}；两者的 id、物品、价格、
     * 类型与库存相同，因此列表形状不变。弹匣以空弹匣出售（无 CUSTOM_DATA）。
     */
    private static ShopEntry entry(UsecShopRules.EntrySpec spec) {
        ShopEntry defaultEntry = builder(spec).build();
        if (!spec.kind().topsUpStacks()) {
            return defaultEntry;
        }
        return builder(spec).onBuy(player -> UsecShopPurchase.buyRound(player, defaultEntry)).build();
    }

    private static ShopEntry.Builder builder(UsecShopRules.EntrySpec spec) {
        ShopEntry.Builder builder = new ShopEntry.Builder(spec.id(), item(spec.kind()).getDefaultStack(), spec.price(),
                spec.type());
        if (spec.hasStockLimit()) {
            builder.stock(spec.stock());
        }
        return builder;
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
