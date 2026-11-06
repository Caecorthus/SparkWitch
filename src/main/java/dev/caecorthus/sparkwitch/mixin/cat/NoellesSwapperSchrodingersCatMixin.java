package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SwapperC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), the FiendFeign Swapper guard for a cat in its
 * spectre: the receiver {@code lambda$registerPackets$4} checks only that neither target is swallowed, so a spectre
 * (alive in Wathe, a pinned spectator) could be swapped and the other player teleported onto its anchor. A request
 * naming a spectre is dropped at HEAD, before NoellesRoles sets the Swapper's cooldown. Additive; every other request
 * is untouched. Class-level {@code remap = false}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），即魔人假死的换位者守卫用于幽魂阶段的猫：接收器
 * {@code lambda$registerPackets$4} 只检查双方都未被吞，因此幽魂（在 Wathe 中存活、被固定的旁观者）可能被换位，另一名玩家会被
 * 传送到其锚点。凡指定了幽魂的请求都在 HEAD 处丢弃，此时 NoellesRoles 尚未设置换位者冷却。叠加式；其他请求不受影响。
 * 类级 {@code remap = false}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesSwapperSchrodingersCatMixin {
    @Inject(
            method = "lambda$registerPackets$4(Lorg/agmas/noellesroles/packet/SwapperC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void sparkwitch$swapperSkipsCatSpectre(
            SwapperC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (payload != null
                && (SchrodingersCatService.isSpectre(payload.player())
                || SchrodingersCatService.isSpectre(payload.player2()))) {
            ci.cancel();
        }
    }
}
