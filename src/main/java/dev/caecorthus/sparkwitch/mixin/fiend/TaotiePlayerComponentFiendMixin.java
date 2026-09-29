package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinExclusion;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner decisions D1 and D4: a dormant Fiend cannot
 * be swallowed, so it must not keep the Taotie's "swallowed everyone" win waiting. Wraps the only
 * {@code isPlayerPlayingAndAlive} check in {@code hasSwallowedEveryone}; every other player keeps the host answer.
 * Pinned by {@code FiendWinExclusionContractTest}. Additive {@code @WrapOperation}, never {@code @Redirect}.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），所有者决定 D1 与 D4：休眠魔人无法被吞噬，因此不得让饕餮的
 * “吞尽所有人”胜利一直等待。只包装 {@code hasSwallowedEveryone} 中唯一的 {@code isPlayerPlayingAndAlive} 判断；
 * 其他玩家保持宿主原本的结果。由 {@code FiendWinExclusionContractTest} 固定。叠加式 {@code @WrapOperation}，
 * 绝不使用 {@code @Redirect}。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public abstract class TaotiePlayerComponentFiendMixin {
    @WrapOperation(
            method = "hasSwallowedEveryone",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z"
            ),
            require = 1,
            allow = 1
    )
    private boolean sparkwitch$swallowAllIgnoresDormantFiend(PlayerEntity player, Operation<Boolean> original) {
        return FiendWinExclusion.countsAsAlive(original.call(player), player);
    }
}
