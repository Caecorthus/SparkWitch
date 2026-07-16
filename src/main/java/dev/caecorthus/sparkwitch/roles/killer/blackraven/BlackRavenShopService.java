package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

/** Replaces only Black Raven's shop while retaining Wathe's exact blackout entry. */
public final class BlackRavenShopService {
    private static boolean registered;

    private BlackRavenShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(BlackRavenShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!BlackRavenRules.isBlackRaven(role)) {
            return;
        }
        ShopEntry blackout = context.getEntries().stream()
                .filter(entry -> "blackout".equals(entry.id()))
                .findFirst()
                .orElse(null);
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                "crowbar",
                WatheItems.CROWBAR.getDefaultStack(),
                BlackRavenRules.SHOP_PRICE,
                ShopEntry.Type.TOOL
        ).stock(1).build());
        context.addEntry(new ShopEntry.Builder(
                "poison_vial",
                WatheItems.POISON_VIAL.getDefaultStack(),
                BlackRavenRules.SHOP_PRICE,
                ShopEntry.Type.POISON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "scorpion",
                WatheItems.SCORPION.getDefaultStack(),
                BlackRavenRules.SHOP_PRICE,
                ShopEntry.Type.POISON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "body_bag",
                WatheItems.BODY_BAG.getDefaultStack(),
                BlackRavenRules.SHOP_PRICE,
                ShopEntry.Type.TOOL
        ).build());
        if (blackout != null) {
            context.addEntry(blackout);
        }
    }
}
