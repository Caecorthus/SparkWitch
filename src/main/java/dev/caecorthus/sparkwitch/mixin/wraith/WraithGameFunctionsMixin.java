package dev.caecorthus.sparkwitch.mixin.wraith;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithDeathService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithSessionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures death attempts before provider BEFORE listeners can mutate them.
 * 在提供方 BEFORE 监听器修改击杀尝试前保存死亡快照。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class WraithGameFunctionsMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$captureWraithDeathAttempt(
            ServerPlayerEntity victim,
            boolean spawnBody,
            ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (WraithStateService.isActive(victim)) {
            if (GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
                Box playArea = MapVariablesWorldComponent.KEY.get(victim.getServerWorld()).getPlayArea();
                if (playArea != null && victim.getY() < playArea.minY) {
                    WraithSessionService.terminateAsSpectator(victim);
                }
            }
            ci.cancel();
            return;
        }
        WraithDeathService.captureAttempt(victim, deathReason);
    }

    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("TAIL")
    )
    private static void sparkwitch$finishConfirmedWraithDeath(
            ServerPlayerEntity victim,
            boolean spawnBody,
            ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        WraithDeathService.finishConfirmedDeath(victim, deathReason);
    }
}
