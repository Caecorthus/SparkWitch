package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatSettlement;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayWinScope;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During the Stowaway's {@link StowawayWinScope} around Wathe's win dispatch (the one scope for "inside the win
 * check"), an untransformed cat is not alive for any caller in the win path. Outside that scope the host answer is
 * untouched, so tasks, targeting, interactions and the round-end card keep the real living state. Server only.
 * 在偷渡客的 {@link StowawayWinScope}（即“胜负判定内”的唯一作用域）包裹的 Wathe 胜负分发期间，未转化的猫对胜利路径上的
 * 所有调用者都不算存活。作用域之外保持宿主原判定，因此任务、索敌、交互与结算卡片仍看到真实存活状态。仅服务端。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class GameFunctionsSchrodingersCatSettlementMixin {
    @Inject(method = "isPlayerPlayingAndAlive", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$catCountsAsDeadDuringWinCheck(
            PlayerEntity player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (StowawayWinScope.isActive() && SchrodingersCatSettlement.isExcluded(player)) {
            cir.setReturnValue(false);
        }
    }
}
