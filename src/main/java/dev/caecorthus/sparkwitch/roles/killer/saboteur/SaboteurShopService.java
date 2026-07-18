package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Replaces only Saboteur's shop while preserving Wathe's exact blackout entry. */
final class SaboteurShopService {
    private static boolean registered;

    private SaboteurShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SaboteurShopService::buildEntries);
    }

    static void initializePromotionStock(ServerPlayerEntity player) {
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        // Do not call initializeShop(): it would erase a surviving team blackout cooldown.
        // 不调用 initializeShop()，否则会清除晋升前仍在生效的全队熄灯冷却。
        ((SaboteurShopStockAccess) shop).sparkwitch$initializeSaboteurLockpickStock();
        shop.sync();
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SaboteurRules.isSaboteur(role)) {
            return;
        }

        // Keep Wathe's instance so its dynamic price, callback, and shared cooldown remain unchanged.
        // 保留 Wathe 原条目，使动态价格、回调与共享冷却完全不变。
        ShopEntry blackoutEntry = context.getEntries().stream()
                .filter(entry -> "blackout".equals(entry.id()))
                .findFirst()
                .orElse(null);

        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                "lockpick",
                WatheItems.LOCKPICK.getDefaultStack(),
                SaboteurRules.LOCKPICK_PRICE,
                ShopEntry.Type.TOOL
        ).stock(1).build());
        if (blackoutEntry != null) {
            context.addEntry(blackoutEntry);
        }
    }
}
