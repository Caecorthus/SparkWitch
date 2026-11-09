package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server knife receiver (Wathe member, {@code remap = false}), HEAD priority 900 so it runs before the default-1000
 * knife guards (SparkTraits, SparkStrength Veteran/Silencer, the Knight duel): it only drops, with no side effects, a
 * stab by an Otherworld Specter on a living player the targeting rule rejects
 * ({@link SpecterOtherworldService#rejectsKnifeStab}), so Wathe never records it, plays the stab or writes the knife
 * cooldown. Every other stab returns untouched and runs Wathe's receiver with all its own checks and guards; this
 * handler never kills, and never touches non-player targets (Black Raven crow, Seeker devices, Magician puppets,
 * drones).
 * 服务端刀刺接收器（Wathe 成员，{@code remap = false}），HEAD 优先级 900，先于默认 1000 的刀守卫（SparkTraits、SparkStrength
 * 老兵/沉默者、骑士决斗）：只无副作用地丢弃里世界幽魂刺向被索敌规则拒绝的存活玩家的刺击
 * （{@link SpecterOtherworldService#rejectsKnifeStab}），使 Wathe 不记录、不播放刺击音、不写小刀冷却。其他所有刺击原样返回，
 * 走 Wathe 接收器及其全部检查与守卫；本处理器从不击杀，也从不触碰非玩家目标（黑羽鸦乌鸦、搜寻者装置、魔术师人偶、无人机）。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false, priority = 900)
public abstract class SpecterOtherworldKnifeMixin {
    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$dropRejectedOtherworldStab(
            KnifeStabPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (SpecterOtherworldService.rejectsKnifeStab(context.player(), payload.target())) {
            ci.cancel();
        }
    }
}
