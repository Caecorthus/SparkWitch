package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Mirrors Wathe's Veteran during a blackout: the Savior receives night vision for the blackout duration and cancels
 * the normal blindness effect through Wathe's public {@link BlackoutEffect} hook.
 * 关灯期间与 Wathe 老兵一致：救世主获得等时长的夜视，并通过 Wathe 的 {@link BlackoutEffect} 公共钩子取消普通失明。
 */
public final class SaviorBlackoutService {
    private static boolean registered;

    private SaviorBlackoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register((player, duration) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)
                    || GameWorldComponent.KEY.get(serverPlayer.getWorld()).getRole(serverPlayer)
                    != SparkWitchRoles.savior()) {
                return null;
            }
            serverPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, duration, 0, false, false, true));
            return BlackoutEffect.BlackoutResult.cancel();
        });
    }
}
