package dev.caecorthus.sparkwitch.mixin.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanSettlement;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayWinScope;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During the Stowaway's {@link StowawayWinScope} around Wathe's win dispatch (the one scope for "inside the win
 * check"), a Charon without a running Soul Moment of his own is not alive for any caller in the win path: the Witch
 * and Murderous Witch counts, and every other {@code CheckWinCondition} listener that asks Wathe who is alive. The
 * Soul Moment's own listener is unaffected, because a moment with souls lifts the exclusion. Outside that scope the
 * host answer is untouched, so the pardon, the ferry, tasks, targeting and the round-end card keep the real living
 * state. Server only.
 * 在偷渡客的 {@link StowawayWinScope}（即“胜负判定内”的唯一作用域）包裹的 Wathe 胜负分发期间，没有自己正在进行的亡魂时刻的
 * 卡戎对胜利路径上的所有调用者都不算存活：魔女与杀意魔女的计数，以及其他所有向 Wathe 询问存活状态的
 * {@code CheckWinCondition} 监听器。亡魂时刻自己的监听器不受影响，因为拥有灵魂的时刻会解除排除。作用域之外保持宿主原判定，
 * 因此宽恕、摆渡、任务、索敌与结算卡片仍看到真实存活状态。仅服务端。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class GameFunctionsFerrymanSettlementMixin {
    @Inject(method = "isPlayerPlayingAndAlive", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$soullessCharonCountsAsDeadDuringWinCheck(
            PlayerEntity player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (StowawayWinScope.isActive() && FerrymanSettlement.isExcluded(player)) {
            cir.setReturnValue(false);
        }
    }
}
