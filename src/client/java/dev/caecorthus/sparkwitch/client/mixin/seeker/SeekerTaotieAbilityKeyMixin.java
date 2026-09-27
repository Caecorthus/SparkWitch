package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.SeekerTaotieClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side prediction for the Taotie's shared G key: {@code KeyBinding#wasPressed} RETURN (vanilla member, remapped).
 * Only when the original result is true, {@code this} is {@code NoellesrolesClient.abilityBind}, the local player is a
 * ready Taotie and the vanilla crosshair is on a Search Car in reach, the press reads as false (NoellesRoles sends no
 * {@code taotie_swallow}, so a player hidden behind the car is never swallowed) and {@code seeker_car_swallow} is queued
 * for the END tick. Every other key and role is untouched. Independent of {@code KeyBindingFearSkillMixin}'s order: if
 * Fear blocks first we see false and do nothing; if we claim first, Fear sees false. A Control Expert stun cancels at
 * HEAD, so this hook never runs then. The server re-validates the request.
 * 饕餮共享 G 键的客户端预测：对 {@code KeyBinding#wasPressed} 做 RETURN 注入（原版成员，需重映射）。仅当原始结果为真、
 * {@code this} 为 {@code NoellesrolesClient.abilityBind}、本地玩家是就绪的饕餮且原版准星落在距离内的搜寻小车上时，
 * 本次按键读作 false（NoellesRoles 不发送 {@code taotie_swallow}，车后的玩家因此不会被吞），并把
 * {@code seeker_car_swallow} 排到 END 刻发送。其他按键和职业不受影响。与 {@code KeyBindingFearSkillMixin} 的执行顺序
 * 无关：Fear 先拦截时我们看到 false 并不做任何事；我们先认领时 Fear 看到 false。控场专家眩晕在 HEAD 取消，
 * 此时本钩子不会执行。服务端会重新校验请求。
 */
@Mixin(KeyBinding.class)
public abstract class SeekerTaotieAbilityKeyMixin {
    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$claimSearchCarSwallow(CallbackInfoReturnable<Boolean> cir) {
        int carEntityId = SeekerTaotieClient.claimPress((KeyBinding) (Object) this, cir.getReturnValueZ());
        if (carEntityId >= 0) {
            cir.setReturnValue(false);
            SeekerTaotieClient.queueSwallow(carEntityId);
        }
    }
}
