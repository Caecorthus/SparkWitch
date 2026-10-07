package dev.caecorthus.sparkwitch.mixin.tofana;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.caecorthus.sparkwitch.item.tofana.TofanaProtectionService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tofana precedes Traits' priority-900 terminal escape injection at the same invocation.
// 同一调用点上，托法娜先于 Traits priority=900 的最终脱险判定。
@Mixin(value = GameFunctions.class, priority = 1000)
public abstract class GameFunctionsTofanaProtectionMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;changeGameMode(Lnet/minecraft/world/GameMode;)Z",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private static void sparkwitch$protectWithTofana(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (SparkTraitsKillerBridge.isLastEscapeActive(victim)) {
            return;
        }
        // Consume and enqueue even when the blade will kill the holder; retaliation has its own death reason.
        // 即使剑将杀死持有者，仍消耗并入队；反杀使用独立死因。
        if (CeremonialSwordProtectionPolicy.cancelsDeath(
                TofanaProtectionService.protect(victim, killer, force), deathReason)) {
            ci.cancel();
        }
    }
}
