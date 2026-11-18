package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShopPurchase;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Sniper shop: Springfield .30-06 service ammunition (weapon frame, unlimited) then a crowbar (tool frame, one per
 * round), both 25. SparkTraits entries are preserved across the rebuild. A bought round first tops up a shown .30-06
 * stack through {@link UsecShopPurchase#buyRound} (the USEC / BEAR round precedent), else falls back to Wathe's default
 * insertion with SparkFactionAPI's second-row wrapper, so each purchase no longer takes its own slot and "nothing fits"
 * still charges nothing. The tablet is issued at round start and never sold here.
 * 狙击手商店：春田 .30-06 制式步枪弹（武器类边框，不限量）与撬棍（工具类边框，每局限购一件），各 25 块。重建时保留
 * SparkTraits 词条。买到的子弹先经 {@link UsecShopPurchase#buyRound} 补进显示中的 .30-06 物品堆（沿用 USEC / BEAR 子弹先例），
 * 否则回退到带 SparkFactionAPI 第二行包装的 Wathe 默认插入，因此每次购买不再各占一格，“放不下”时仍不扣钱。平板在开局发放，
 * 此处不出售。
 */
public final class SniperShopService {
    private static boolean registered;

    private SniperShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SniperShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SniperRules.isSniperRole(role)) {
            return;
        }
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(ammoEntry());
        context.addEntry(new ShopEntry.Builder(
                SniperRules.CROWBAR_ENTRY_ID,
                WatheItems.CROWBAR.getDefaultStack(),
                SniperRules.CROWBAR_PRICE,
                ShopEntry.Type.TOOL
        ).stock(SniperRules.CROWBAR_STOCK).build());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    /**
     * Both entries share id, stack, price and type, so the list shape is unchanged; the handler-less twin is the default
     * insertion the top-up falls back to. Purchases run server-side ({@code PlayerShopComponent.tryBuy}).
     * 两个条目的 id、物品、价格与类型相同，因此列表形状不变；不带处理器的孪生条目是补入失败时回退的默认插入。购买在服务端执行
     * （{@code PlayerShopComponent.tryBuy}）。
     */
    private static ShopEntry ammoEntry() {
        ShopEntry defaultEntry = ammoBuilder().build();
        return ammoBuilder().onBuy(player -> UsecShopPurchase.buyRound(player, defaultEntry)).build();
    }

    private static ShopEntry.Builder ammoBuilder() {
        return new ShopEntry.Builder(
                SniperRules.AMMO_ENTRY_ID,
                new ItemStack(SparkWitchItems.ammo3006()),
                SniperRules.AMMO_PRICE,
                ShopEntry.Type.WEAPON
        );
    }
}
