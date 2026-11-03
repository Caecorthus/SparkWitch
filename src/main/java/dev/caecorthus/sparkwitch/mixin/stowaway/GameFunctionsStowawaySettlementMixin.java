package dev.caecorthus.sparkwitch.mixin.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawaySettlement;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayWinScope;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During the {@link StowawayWinScope} around Wathe's win dispatch, a Stowaway is not alive for every caller in the win
 * path. Outside that scope the host answer is untouched, so tasks, targeting, interactions and the round-end card keep
 * seeing the real living state. Server only.
 * 在 Wathe 胜负分发期间的 {@link StowawayWinScope} 内，偷渡客对所有胜利路径调用者都不算存活。作用域之外保持宿主原判定，
 * 因此任务、索敌、交互与结算卡片仍看到真实存活状态。仅服务端。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class GameFunctionsStowawaySettlementMixin {
    @Inject(method = "isPlayerPlayingAndAlive", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$stowawayCountsAsDeadDuringWinCheck(
            PlayerEntity player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (StowawayWinScope.isActive() && StowawaySettlement.isExcluded(player)) {
            cir.setReturnValue(false);
        }
    }
}
