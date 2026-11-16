package dev.caecorthus.sparkwitch.mixin.hiro;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroVictimFeignService;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner rule 3 of 二阶堂希罗's 死亡回归: a fake-dead
 * victim counts as ALIVE. NoellesRoles' shared {@code countAliveAndNotSwallowed} counter skips every spectator, so
 * during the at most 15 s of a fake death it would count the victim out and could start a moment that cannot be undone
 * (Corrupt Cop, Taotie, Survival Master: the last two end the round) although the victim may still get up. Only the
 * counter's single {@code isSpectator()} read is wrapped, and only a fake-dead victim's answer changes (to "not a
 * spectator"); the Fiend's wrapper on the neighbouring {@code isPlayerPlayingAndAlive} read is independent. The win
 * listener itself already counts the victim alive ({@code isPlayerPlayingAndAlive}). Additive {@code @WrapOperation},
 * never {@code @Redirect}. Class-level {@code remap = false}; the vanilla {@code isSpectator} target says
 * {@code remap = true}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），二阶堂希罗「死亡回归」所有者规则 3：假死的受害者算作存活。
 * NoellesRoles 共用的 {@code countAliveAndNotSwallowed} 计数器会跳过所有旁观者，因此在至多 15 秒的假死期间它会把受害者算作
 * 出局，并可能触发无法撤销的时刻（黑警、饕餮、生存大师：后两者会结束回合），而受害者仍可能起身。只包装计数器中唯一一次
 * {@code isSpectator()} 读取，且只改变假死受害者的结果（改为“非旁观者”）；魔人对相邻 {@code isPlayerPlayingAndAlive} 读取的
 * 包装与此互不影响。胜利监听器本身已把受害者算作存活（{@code isPlayerPlayingAndAlive}）。叠加式 {@code @WrapOperation}，绝不
 * 使用 {@code @Redirect}。类级 {@code remap = false}；原版 {@code isSpectator} 目标显式写 {@code remap = true}。仅服务端。
 */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesRolesHiroFeignAliveCountMixin {
    @WrapOperation(
            method = "countAliveAndNotSwallowed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z",
                    remap = true
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$feignedVictimCountsAsAlive(ServerPlayerEntity player, Operation<Boolean> original) {
        return original.call(player) && !HiroVictimFeignService.isFeigned(player);
    }
}
