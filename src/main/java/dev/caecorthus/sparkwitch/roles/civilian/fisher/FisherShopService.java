package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

/** Restricted Angler shop: bait only, SparkTraits entries preserved. / 钓鱼佬专属商店：只卖鱼饵，保留 SparkTraits 条目。 */
public final class FisherShopService {
    private static boolean registered;

    private FisherShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(FisherShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        // Stock is cached while STARTING; role alone gates the shop. / 库存在 STARTING 阶段缓存，只按职业筛选商店。
        if (!FisherRules.isFisher(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        var preserved = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(FisherRules.BAIT_SHOP_ENTRY_ID,
                SparkWitchItems.fishBait().getDefaultStack(), FisherRules.BAIT_PRICE, ShopEntry.Type.TOOL).build());
        SparkTraitsShopEntryPreserver.restore(context, preserved);
    }
}
