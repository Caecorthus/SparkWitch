package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.item.FlashlightItem;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Lets an actively held flashlight suppress only the current blackout blindness refresh.
 * 让手持开启的手电筒只压制当前熄灯刷新出来的失明。
 */
public final class FlashlightBlackoutService {
    private static final int BLACKOUT_BLINDNESS_DURATION_TOLERANCE_TICKS = 2;
    private static boolean registered;

    private FlashlightBlackoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(FlashlightBlackoutService::beforeBlackoutEffect);
    }

    private static BlackoutEffect.BlackoutResult beforeBlackoutEffect(ServerPlayerEntity player, int durationTicks) {
        if (!shouldCancelBlackoutBlindness(
                GameFunctions.isPlayerPlayingAndAlive(player),
                FlashlightItem.isHeldOn(player)
        )) {
            return null;
        }

        removeCurrentBlackoutBlindness(player, durationTicks);
        return BlackoutEffect.BlackoutResult.cancel();
    }

    static boolean shouldCancelBlackoutBlindness(boolean playerPlayingAndAlive, boolean holdingLitFlashlight) {
        return playerPlayingAndAlive && holdingLitFlashlight;
    }

    static boolean matchesCurrentBlackoutBlindness(int blindnessDurationTicks, int blackoutDurationTicks) {
        return Math.abs(blindnessDurationTicks - blackoutDurationTicks) <= BLACKOUT_BLINDNESS_DURATION_TOLERANCE_TICKS;
    }

    private static void removeCurrentBlackoutBlindness(ServerPlayerEntity player, int durationTicks) {
        StatusEffectInstance blindness = player.getStatusEffect(StatusEffects.BLINDNESS);
        if (blindness != null && matchesCurrentBlackoutBlindness(blindness.getDuration(), durationTicks)) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }
}
