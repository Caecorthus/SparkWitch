package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SwapperC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}): the Swapper receiver {@code lambda$registerPackets$4}
 * checks only that neither target is swallowed, so a feigning Fiend (alive in Wathe, a spectator on its fake corpse)
 * could be swapped and the other player teleported onto the corpse. A request naming a feigning Fiend is dropped at
 * HEAD, before NoellesRoles sets the Swapper's cooldown, the same silent path a swallowed target takes. Additive;
 * SparkTraits' HEAD guards on the same lambda are independent. Class-level {@code remap = false}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}）：换位者接收器 {@code lambda$registerPackets$4} 只检查双方都未被吞，
 * 因此假死中的魔人（在 Wathe 中存活、以旁观者身份停在假尸体上）可能被换位，另一名玩家会被传送到尸体上。凡指定了假死魔人的请求
 * 都在 HEAD 处丢弃，此时 NoellesRoles 尚未设置换位者冷却，与目标被吞时的静默分支相同。叠加式；同一 lambda 上 SparkTraits 的
 * HEAD 守卫互不影响。类级 {@code remap = false}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesSwapperFiendFeignMixin {
    @Inject(
            method = "lambda$registerPackets$4(Lorg/agmas/noellesroles/packet/SwapperC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void sparkwitch$swapperSkipsFeigningFiend(
            SwapperC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (payload == null) {
            return;
        }
        if (FiendFeignService.isFeigning(context.server(), payload.player())
                || FiendFeignService.isFeigning(context.server(), payload.player2())) {
            ci.cancel();
        }
    }
}
