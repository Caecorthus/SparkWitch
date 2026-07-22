package dev.caecorthus.sparkwitch.roles.witch.curser;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurShopStockAccess;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Owns Curser's one-item promotion shop. / 持有诅咒师的单物品晋升商店。 */
final class CurserShopService {
    static final int LOCKPICK_PRICE = 50;
    private static boolean registered;

    private CurserShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(CurserShopService::buildEntries);
    }

    static void initializePromotionStock(ServerPlayerEntity player) {
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        // Do not call initializeShop(): promotion must not reset Wathe-owned state.
        // 不调用 initializeShop()：晋升不能重置 Wathe 自有状态。
        ((SaboteurShopStockAccess) shop).sparkwitch$initializePromotionLockpickStock();
        shop.sync();
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!CurserFeatureService.isActivePromotedCurser(player)) {
            return;
        }
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                "lockpick",
                WatheItems.LOCKPICK.getDefaultStack(),
                LOCKPICK_PRICE,
                ShopEntry.Type.TOOL
        ).stock(1).build());
    }
}
