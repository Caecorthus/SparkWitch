package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Builds the Accomplice shop without granting native killer-shop access.
 * 构建共犯专属商店，不把共犯加入 wathe 原生杀手商店权限。
 */
public final class AccompliceShopService {
    private static boolean registered;

    private AccompliceShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(AccompliceShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!GrandWitchRules.isAccomplice(role)) {
            return;
        }

        context.clearEntries();
        AccompliceShopRules.entries().forEach(context::addEntry);
    }
}
