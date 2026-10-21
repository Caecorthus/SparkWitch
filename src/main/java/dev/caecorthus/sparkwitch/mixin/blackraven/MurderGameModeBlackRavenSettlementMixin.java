package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenSettlement;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam W1 (plan v2 §3.3, owner 2026-10-21; pinned Wathe {@code 1.5.6-spark}): a living Black Raven is never
 * a living civilian for Wathe's killer-win check, so "Raven + killers only" ends in a KILLERS win and settlement comes
 * out as if the Raven were absent. Wraps only the FIRST {@code isPlayerPlayingAndAlive} call of
 * {@code tickServerGameLoop} (ordinal 0, the civilian-alive loop guarded by {@code isInnocent}, which SparkTraits
 * redirects and this seam leaves alone); the second call (the killer-team passenger check) stays untouched. Raw role
 * through {@link BlackRavenSettlement}; every other player keeps Wathe's answer. Additive {@code @WrapOperation},
 * never {@code @Redirect}. Server only.
 * 外部接缝 W1（计划 v2 §3.3，所有者 2026-10-21；固定 Wathe {@code 1.5.6-spark}）：存活的黑羽鸦在 Wathe 杀手胜利判定中
 * 永远不算存活好人，因此“只剩黑羽鸦与杀手”时判杀手胜利，结算结果与黑羽鸦不在场时相同。只包装
 * {@code tickServerGameLoop} 中的第一处 {@code isPlayerPlayingAndAlive}（序号 0，由 {@code isInnocent} 守卫的好人存活循环；
 * SparkTraits 重定向了该 {@code isInnocent}，本接缝不触碰它）；第二处（杀手阵营的乘客胜利判定）保持不变。通过
 * {@link BlackRavenSettlement} 按原始职业判断；其他玩家保持 Wathe 原本的结果。叠加式 {@code @WrapOperation}，绝不使用
 * {@code @Redirect}。仅服务端。
 */
@Mixin(value = MurderGameMode.class, remap = false)
public abstract class MurderGameModeBlackRavenSettlementMixin {
    @WrapOperation(
            method = "tickServerGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 0
            ),
            require = 1,
            allow = 1
    )
    private boolean sparkwitch$killerWinIgnoresBlackRaven(PlayerEntity player, Operation<Boolean> original) {
        return BlackRavenSettlement.countsAsAlive(original.call(player), player);
    }
}
