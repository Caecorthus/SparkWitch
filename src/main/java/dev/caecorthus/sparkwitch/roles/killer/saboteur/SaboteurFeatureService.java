package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers Saboteur-owned shop and post-promotion task income.
 * 注册破坏者自有商店与晋升后的任务收入。
 */
public final class SaboteurFeatureService {
    private static boolean registered;

    private SaboteurFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SaboteurLightOutageService.register();
        SaboteurShopService.register();
    }

    /**
     * Initializes role-owned cooldown and stock after the promotion transition that bypasses RoleAssigned.
     * 在绕过 RoleAssigned 的晋升切换后初始化职业自有冷却与库存。
     */
    public static void initializePromotion(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SaboteurRules.isSaboteur(role) || !SaboteurRules.isActivePromotedSaboteur(player)) {
            return;
        }
        SaboteurPlayerComponent component = SaboteurPlayerComponent.KEY.get(player);
        component.setCooldownTicks(SaboteurRules.INITIAL_COOLDOWN_TICKS);
        SaboteurShopService.initializePromotionStock(player);
        if (component.claimPromotionWalkieGrant()) {
            player.giveItemStack(new ItemStack(WatheItems.WALKIE_TALKIE));
        }
    }

}
