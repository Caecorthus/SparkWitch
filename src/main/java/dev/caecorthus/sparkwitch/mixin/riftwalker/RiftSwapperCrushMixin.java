package dev.caecorthus.sparkwitch.mixin.riftwalker;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper.RiftSwapperCrushService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SwapperC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D13: a Swapper who targets a Rift occupant is crushed and NoellesRoles' swap never starts (no wake-up, unmount,
 * sounds, teleport, cooldown or swap record). HEAD is the only point before NR's first side effect. The full
 * descriptor with {@code require = 1} makes a rebuilt NR jar fail at load instead of silently dropping the guard.
 * Priority 1100: HEAD callbacks run in ascending mixin priority, so the default-1000 HEAD guards on the same handler
 * (SparkFactionAPI's affect veto, SparkTraits' silence/Last Stand refusal) decide first; a payload they cancel never
 * reaches the crush. The decision itself lives in {@link RiftSwapperCrushService#intercept}.
 * D13：交换者以门内玩家为目标时被夹死，NoellesRoles 的交换不会开始（不唤醒、不下坐骑、不播音效、不传送、不上冷却、
 * 不记交换）。HEAD 是 NR 第一个副作用之前唯一的位置。完整描述符加 {@code require = 1}，NR jar 重编后会在加载时报错，
 * 而不是悄悄失去守卫。优先级 1100：HEAD 回调按 mixin 优先级升序执行，同一处理器上默认 1000 的 HEAD 守卫
 * （SparkFactionAPI 影响否决、SparkTraits 沉默/Last Stand 拒绝）先行裁决；被它们取消的数据包不会走到夹死逻辑。
 * 判定本身在 {@link RiftSwapperCrushService#intercept}。
 */
@Mixin(value = Noellesroles.class, priority = 1100)
public abstract class RiftSwapperCrushMixin {
    @Inject(
            method = "lambda$registerPackets$4(Lorg/agmas/noellesroles/packet/SwapperC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void sparkwitch$crushSwapperTargetingRiftOccupant(SwapperC2SPacket payload,
                                                                    ServerPlayNetworking.Context context,
                                                                    CallbackInfo ci) {
        if (RiftSwapperCrushService.intercept(context.player(), payload.player(), payload.player2())) {
            ci.cancel();
        }
    }
}
