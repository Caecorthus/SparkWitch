package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.PotionShellBurn;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Potion Gunner registration entry point (called once from SparkWitchEvents). The accomplice-pool registration is
 * added when the shared base lands.
 * 药炮手注册入口（由 SparkWitchEvents 调用一次）。共犯池登记在公共底座合入后补上。
 */
public final class PotionGunnerFeatureService {
    private static boolean registered;

    private PotionGunnerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PotionGunnerShopService.register();
        PotionGunnerLifecycle.register();
        PotionBlastService.register();
        PotionShellBurn.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && PotionGunnerRules.isPotionGunner(role)) {
                PotionGunnerLoadoutService.ensureLauncher(serverPlayer);
            }
        });
    }
}
