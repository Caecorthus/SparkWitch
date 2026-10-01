package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Grants the bound launcher. Idempotent: called on role assignment (forced roles) and again from the accomplice
 * pool's post-recruit hook, because recruitment rewrites the whole inventory after {@code RoleAssigned}.
 * 发放绑定的炮筒。幂等：在职业分配时（强制指定职业）调用，并在共犯池的招募完成钩子里再调用一次，
 * 因为招募会在 {@code RoleAssigned} 之后整体重写背包。
 */
public final class PotionGunnerLoadoutService {
    private PotionGunnerLoadoutService() {
    }

    public static void ensureLauncher(ServerPlayerEntity player) {
        // WP1 implements. / 由 WP1 实现。
    }
}
