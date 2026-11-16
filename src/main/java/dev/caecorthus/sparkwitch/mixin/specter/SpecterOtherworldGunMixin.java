package dev.caecorthus.sparkwitch.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server gun receiver (Wathe member, {@code remap = false}), HEAD priority 900 so it runs before the default-1000 gun
 * guards (SparkFactionAPI, SparkTraits, the police gun parity mixins, the Knight duel): it only drops, with no side
 * effects, a shot by an Otherworld Specter at a living player the targeting rule rejects
 * ({@link SpecterOtherworldService#rejectsGunShot}). Wathe resolves the shot-an-innocent branch before the kill, so
 * without this drop a shot the {@code killPlayer} HEAD rule voids would still cost the Specter the revolver, spend a
 * derringer, record the use and write the cooldown. Every other shot returns untouched and runs Wathe's receiver with
 * all its own checks and guards; this handler never kills, and a shot the Dominator forces is never dropped (the rule
 * does not apply to it).
 * 服务端枪械接收器（Wathe 成员，{@code remap = false}），HEAD 优先级 900，先于默认 1000 的枪械守卫（SparkFactionAPI、
 * SparkTraits、警察枪械一致性 mixin、骑士决斗）：只无副作用地丢弃里世界幽魂射向被索敌规则拒绝的存活玩家的射击
 * （{@link SpecterOtherworldService#rejectsGunShot}）。Wathe 在击杀之前就结算误杀无辜分支，因此若不丢弃，这次会被
 * {@code killPlayer} HEAD 规则作废的射击仍会让幽魂失去左轮、消耗德林加、记录使用并写入冷却。其他所有射击原样返回，
 * 走 Wathe 接收器及其全部检查与守卫；本处理器从不击杀，也从不丢弃支配者强迫开出的那一枪（规则不适用于它）。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false, priority = 900)
public abstract class SpecterOtherworldGunMixin {
    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$dropRejectedOtherworldShot(
            GunShootPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (SpecterOtherworldService.rejectsGunShot(context.player(), payload.target())) {
            ci.cancel();
        }
    }
}
