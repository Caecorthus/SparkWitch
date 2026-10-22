package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenSettlement;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), plan v2 §3.3: the Black Raven never counts as alive
 * for settlement, so it never keeps the Pathogen's "every other living player is infected" win waiting. Wraps only the
 * per-player {@code isPlayerPlayingAndAlive} check of that inner loop in the win listener
 * {@code lambda$registerEvents$14} (ordinal 3: the loop over every other player that sets the "all infected" flag);
 * the Pathogen's own alive checks (ordinals 2 and 4) and every other check stay untouched. Unlike
 * {@code FiendWinExclusion}, this wrap skips ONLY the Black Raven (the dormant Fiend keeps the old Pathogen behaviour,
 * plan §3.3). The ordinal is pinned by {@code BlackRavenSettlementContractTest}. Additive {@code @WrapOperation},
 * never {@code @Redirect}; raw role through {@link BlackRavenSettlement}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），计划 v2 §3.3：黑羽鸦在结算中永不算存活，因此不会让病原体的
 * “其他存活玩家全部被感染”胜利一直等待。只包装胜利监听器 {@code lambda$registerEvents$14} 中该内层循环的逐人
 * {@code isPlayerPlayingAndAlive} 判断（序号 3：遍历其他所有玩家并设置“全部感染”标记的循环）；病原体自身的存活判断
 * （序号 2 与 4）及其他所有判断保持不变。与 {@code FiendWinExclusion} 不同，这里只跳过黑羽鸦（休眠魔人保持病原体的旧行为，
 * 计划 §3.3）。序号由 {@code BlackRavenSettlementContractTest} 固定。叠加式 {@code @WrapOperation}，绝不使用
 * {@code @Redirect}；通过 {@link BlackRavenSettlement} 按原始职业判断。仅服务端。
 */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesRolesPathogenBlackRavenSettlementMixin {
    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 3
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$pathogenWinIgnoresBlackRaven(PlayerEntity player, Operation<Boolean> original) {
        return BlackRavenSettlement.countsAsAlive(original.call(player), player);
    }
}
