package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantHooks;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.PotionShellBurn;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Potion Gunner registration entry point (called once from SparkWitchEvents, after roles are registered). It joins
 * the special-accomplice pool, so a Bewitched promotion may roll it at most once per round; the launcher is granted
 * from {@code RoleAssigned} and again, idempotently, once the promotion commits.
 * 药炮手注册入口（由 SparkWitchEvents 在职业注册后调用一次）。它加入特殊共犯池，魔化使晋升每局最多抽到一次；炮筒在
 * {@code RoleAssigned} 时发放，并在晋升提交后再幂等地确保一次。
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
        PotionShellBurn.register();
        AccompliceVariants.register(SparkWitchRoles.potionGunner(), new AccompliceVariantHooks() {
            @Override
            public void afterPromotionCommitted(ServerPlayerEntity player) {
                PotionGunnerLoadoutService.ensureLauncher(player);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && PotionGunnerRules.isPotionGunner(role)) {
                PotionGunnerLoadoutService.ensureLauncher(serverPlayer);
            }
        });
    }
}
