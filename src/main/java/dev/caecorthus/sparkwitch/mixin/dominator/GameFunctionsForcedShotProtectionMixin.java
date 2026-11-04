package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the forced-shot protection is active on a player, no role-specific post-kill rule may kill them with
 * {@code shot_innocent}. The gun receiver's own punishment is already cancelled in
 * {@link DominatorForcedShotGunMixin}; this covers listeners registered on {@code KillPlayer.AFTER}.
 * 强制开枪保护生效期间，任何职业的击杀后规则都不能以 {@code shot_innocent} 处死该玩家。枪械接收器自身的惩罚
 * 已由 {@link DominatorForcedShotGunMixin} 取消；此处覆盖 {@code KillPlayer.AFTER} 上的监听器。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsForcedShotProtectionMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$blockForcedShotInnocentDeath(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (GameConstants.DeathReasons.SHOT_INNOCENT.equals(deathReason)
                && DominatorForcedShotComponent.isProtectedPlayer(victim)) {
            ci.cancel();
        }
    }
}
