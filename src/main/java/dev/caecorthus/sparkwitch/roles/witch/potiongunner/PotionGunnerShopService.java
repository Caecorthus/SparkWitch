package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Builds the Potion Gunner shop on both sides from the synced role; the plain Accomplice listener keeps an exact
 * Accomplice check, so it never clears this list.
 * 两端根据同步的职业构建药炮手商店；普通共犯的监听器保持精确匹配共犯，因此不会清掉这份列表。
 */
public final class PotionGunnerShopService {
    private static boolean registered;

    private PotionGunnerShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(PotionGunnerShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!PotionGunnerRules.isPotionGunner(role)) {
            return;
        }
        context.clearEntries();
        PotionGunnerShopRules.entries().forEach(context::addEntry);
    }
}
