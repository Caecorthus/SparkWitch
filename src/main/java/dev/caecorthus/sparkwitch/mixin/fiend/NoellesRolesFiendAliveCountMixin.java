package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinExclusion;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}, commit b58fa5f), owner decision D1: a dormant Fiend
 * is not alive for NoellesRoles' last-one-standing counts. Only the per-player {@code isPlayerPlayingAndAlive} check
 * inside three counts is wrapped: the Jester-moment "no other player alive" loop (ordinal 6) and the Corrupt Cop
 * {@code aliveCount == 1} loop (ordinal 9) of the win listener {@code lambda$registerEvents$14}, and the shared
 * {@code countAliveAndNotSwallowed} counter that feeds the Corrupt Cop, Taotie and Survival moment triggers. The
 * listener's other ten checks (Vulture, Survival, Pathogen, Taotie, Shadow Jester, killer team) stay untouched, and
 * {@code NoellesRolesShadowJesterWinMixin}'s {@code neutralWin} inject in the same lambda is independent of these.
 * The ordinals are pinned by {@code FiendWinExclusionContractTest}. Additive {@code @WrapOperation}s, never
 * {@code @Redirect}; the host answer is kept for every player except a dormant Fiend. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}，提交 b58fa5f），所有者决定 D1：休眠魔人在 NoellesRoles 的
 * 最后存活者计数中视为不存活。只包装三处计数中的逐人 {@code isPlayerPlayingAndAlive} 判断：胜利监听器
 * {@code lambda$registerEvents$14} 中的小丑时刻“无其他存活玩家”循环（序号 6）与黑警 {@code aliveCount == 1} 循环
 * （序号 9），以及为黑警、饕餮、生存时刻触发提供人数的共用计数器 {@code countAliveAndNotSwallowed}。监听器中其余十处
 * 判断（秃鹫、生存、病原体、饕餮、影子小丑、杀手阵营）保持不变；同一 lambda 中
 * {@code NoellesRolesShadowJesterWinMixin} 对 {@code neutralWin} 的注入与此互不影响。序号由
 * {@code FiendWinExclusionContractTest} 固定。均为叠加式 {@code @WrapOperation}，绝不使用 {@code @Redirect}；
 * 除休眠魔人外，所有玩家保持宿主原本的结果。仅服务端。
 */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesRolesFiendAliveCountMixin {
    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 6
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$jesterMomentIgnoresDormantFiend(PlayerEntity player, Operation<Boolean> original) {
        return FiendWinExclusion.countsAsAlive(original.call(player), player);
    }

    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 9
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$corruptCopCountIgnoresDormantFiend(PlayerEntity player, Operation<Boolean> original) {
        return FiendWinExclusion.countsAsAlive(original.call(player), player);
    }

    @WrapOperation(
            method = "countAliveAndNotSwallowed",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z"
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$momentTriggerCountIgnoresDormantFiend(PlayerEntity player, Operation<Boolean> original) {
        return FiendWinExclusion.countsAsAlive(original.call(player), player);
    }
}
