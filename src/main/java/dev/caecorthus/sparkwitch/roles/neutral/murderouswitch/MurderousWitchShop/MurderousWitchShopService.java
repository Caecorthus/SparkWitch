package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchShop;

import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules.MurderousWitchRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Builds the Murderous Witch shop without granting native killer-shop access.
 * 构建杀意魔女专属商店，不把她加入 wathe 原生杀手商店权限。
 */
public final class MurderousWitchShopService {
    private static boolean registered;

    private MurderousWitchShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(MurderousWitchShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!MurderousWitchRules.isMurderousWitch(role)) {
            return;
        }

        context.clearEntries();
        MurderousWitchShopRules.entries().forEach(context::addEntry);
    }
}
