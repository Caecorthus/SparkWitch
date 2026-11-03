package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 探索者 shop: spyglass (150) and conductor key (100).
 * 探索者商店：望远镜 150、乘务员钥匙 100。
 */
public final class DiscovererShopService {

    private static boolean registered;

    private DiscovererShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        dev.doctor4t.wathe.api.event.BuildShopEntries.EVENT.register(DiscovererShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, dev.doctor4t.wathe.api.event.BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !DiscovererRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        // Vanilla spyglass, same effect as the base game item.
        // 原版望远镜，效果与原版一致。
        context.addEntry(new ShopEntry.Builder("sparkwitch:discoverer_spyglass",
                new ItemStack(Items.SPYGLASS), DiscovererRules.SPYGLASS_PRICE, ShopEntry.Type.TOOL)
                .cooldown(0)
                .stock(1)
                .build());

        // The role's own conductor key: master-key reach, 7 durability, 3 per train door.
        // 探索者自己的乘务员钥匙：与万能钥匙同开门范围，7 点耐久、每开一次列车门 3 点。
        var keyItem = dev.caecorthus.sparkwitch.SparkWitchItems.conductorKey();
        if (keyItem != Items.AIR) {
            ItemStack key = new ItemStack(keyItem);
            context.addEntry(new ShopEntry.Builder("sparkwitch:discoverer_conductor_key",
                    key, DiscovererRules.CONDUCTOR_KEY_PRICE, ShopEntry.Type.TOOL)
                    .cooldown(DiscovererRules.CONDUCTOR_KEY_COOLDOWN_TICKS)
                    .stock(DiscovererRules.CONDUCTOR_KEY_SHOP_STOCK)
                    .build());
        }
    }
}
