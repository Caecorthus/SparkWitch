package dev.caecorthus.sparkwitch.mixin.savior;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorObservationService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wathe seam for the Savior's observation (owner 2026-11): the sample is taken once per kill attempt instead of every
 * server tick. A {@code @WrapMethod} around the five-argument {@code killPlayer}, to which the shorter overloads
 * delegate (the shape of {@code JudgeKillCompletionMixin} and of SparkFactionAPI's kill snapshot), is the one place
 * that is both early and complete. MixinExtras applies it after every ordinary injector, so it encloses all
 * head-of-kill hooks of every mod and Wathe's {@code KillPlayer.BEFORE} (which is first-result-wins, so a listener
 * there may never be asked): the victim is still in its pre-kill state (mode, position, Wathe-alive) when the sample
 * is taken, and the {@code finally} runs on every exit, so a cancelled, refused or failing attempt can never leave a
 * sample behind. Default priority: the other wrappers around this method (the Judge's, SparkTraits', SparkFactionAPI's)
 * only keep books before they call on, so their order relative to this read does not matter. It reads only; the kill
 * itself is always called exactly once, with its arguments unchanged.
 * 救世主观测的 Wathe 接缝（所有者 2026-11）：采样改为每次击杀尝试一次，而不是每个服务端刻一次。包住五参数 {@code killPlayer}
 * （较短的重载都委托给它）的 {@code @WrapMethod}（与 {@code JudgeKillCompletionMixin} 以及 SparkFactionAPI 的击杀快照形态相同）
 * 是唯一既足够早又完整的位置。MixinExtras 在所有普通注入器之后应用它，因此它包住所有模组在击杀开头的钩子以及 Wathe 的
 * {@code KillPlayer.BEFORE}（该事件以首个结果为准，其中的监听器可能根本不会被询问）：采样时死者仍处于击杀前的状态（模式、位置、
 * Wathe 存活），而 {@code finally} 在每条退出路径上都会执行，因此被取消、被拒绝或出错的尝试绝不会留下采样。使用默认优先级：
 * 包住该方法的其他包装（法官、SparkTraits、SparkFactionAPI）在继续调用之前都只做记录，因此它们与这次读取的相对顺序无关紧要。
 * 它只读取；击杀本身始终恰好调用一次，参数不变。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsSaviorObservationMixin {
    @WrapMethod(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V")
    private static void sparkwitch$sampleSaviorObservers(ServerPlayerEntity victim, boolean spawnBody,
                                                        ServerPlayerEntity killer, Identifier reason, boolean force,
                                                        Operation<Void> original) {
        SaviorObservationService.beginKillAttempt(victim);
        try {
            original.call(victim, spawnBody, killer, reason, force);
        } finally {
            SaviorObservationService.endKillAttempt(victim);
        }
    }
}
