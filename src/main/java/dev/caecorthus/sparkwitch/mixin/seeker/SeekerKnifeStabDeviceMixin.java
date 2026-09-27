package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server knife-stab receiver: breaks a validated device target. Wathe's receiver returns at once for a non-player id,
 * so cancelling a device stab at HEAD skips nothing Wathe would have done (no kill, cooldown, record or Veteran use).
 * Priority 2300 places this callback after SparkTraits' priority-2200 HEAD guard (Last Escape, forced melee cooldown,
 * raised-knife release) so its locks still apply; {@code SeekerDeviceHits} re-checks the weapon-action gate itself, so
 * the order is not load-bearing. Wathe member: class-level {@code remap = false}.
 * 服务端刀刺接收器：打坏经校验的设备目标。Wathe 接收器遇到非玩家 id 会立即返回，因此在 HEAD 取消对设备的刺击不会跳过
 * Wathe 本会执行的任何逻辑（无击杀、冷却、记录或老兵次数）。优先级 2300 使本回调排在 SparkTraits 优先级 2200 的 HEAD 守卫
 * （最后逃脱、强制近战冷却、举刀释放）之后，使其限制仍然生效；{@code SeekerDeviceHits} 自身也会复查武器动作门槛，
 * 因此执行顺序并非关键。Wathe 成员：类级 {@code remap = false}。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false, priority = 2300)
public abstract class SeekerKnifeStabDeviceMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$stabSeekerDevice(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                             CallbackInfo ci) {
        ServerPlayerEntity attacker = context.player();
        if (attacker.getServerWorld().getEntityById(payload.target()) instanceof SeekerDeviceEntity device) {
            SeekerDeviceHits.onKnifeStabPayload(attacker, device);
            ci.cancel();
        }
    }
}
