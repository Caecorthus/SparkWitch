package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintFeatureService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces Saint protection before Wathe's first-result-wins kill event can bypass later listeners.
 * 在 Wathe 的首个结果短路击杀事件绕过后续监听器之前，强制执行圣徒保护。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsSaintProtectionMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$protectSaint(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (killer == null && dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaTerminalService.isBacklash(deathReason)) {
            return;
        }
        // Traits owns active-phase immunity and its train/lifecycle exceptions before protection costs.
        // Traits 在消耗保护前裁定脱险无敌及列车/生命周期例外。
        if (SparkTraitsKillerBridge.isLastEscapeActive(victim)) {
            return;
        }
        if (CeremonialSwordProtectionPolicy.cancelsDeath(
                SaintFeatureService.blocksKill(victim, killer, deathReason), deathReason)) {
            ci.cancel();
        }
    }
}
