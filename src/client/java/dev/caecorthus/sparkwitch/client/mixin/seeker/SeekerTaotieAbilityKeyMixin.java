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
 * ready Taotie and the vanilla crosshair is on a Search Car within 3.0 of the eye, the press reads as false
 * (NoellesRoles sends no {@code taotie_swallow}, so a player hidden behind the car is never swallowed) and, when the car
 * is also within the server's feet-to-feet reach, {@code seeker_car_swallow} is queued for the END tick. Every other
 * key and role is untouched. Independent of {@code KeyBindingFearSkillMixin}'s order (it is listed first, so it runs
 * first): if Fear blocks we see false and do nothing; otherwise it only records the press. A Control Expert stun
 * cancels at HEAD, so this hook never runs then. The server re-validates the request.
 * 饕餮共享 G 键的客户端预测：对 {@code KeyBinding#wasPressed} 做 RETURN 注入（原版成员，需重映射）。仅当原始结果为真、
 * {@code this} 为 {@code NoellesrolesClient.abilityBind}、本地玩家是就绪的饕餮且原版准星落在距眼睛 3.0 格内的搜寻
 * 小车上时，本次按键读作 false（NoellesRoles 不发送 {@code taotie_swallow}，车后的玩家因此不会被吞）；若小车也在
 * 服务端脚到脚距离内，则把 {@code seeker_car_swallow} 排到 END 刻发送。其他按键和职业不受影响。与
 * {@code KeyBindingFearSkillMixin} 的执行顺序无关（它在配置中靠前，先执行）：Fear 拦截时我们看到 false 并不做任何事；
 * 否则它只记录按键。控场专家眩晕在 HEAD 取消，此时本钩子不会执行。服务端会重新校验请求。
 */
@Mixin(KeyBinding.class)
public abstract class SeekerTaotieAbilityKeyMixin {
    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$claimSearchCarSwallow(CallbackInfoReturnable<Boolean> cir) {
        int claim = SeekerTaotieClient.claimPress((KeyBinding) (Object) this, cir.getReturnValueZ());
        if (claim != SeekerTaotieClient.NO_CLAIM) {
            cir.setReturnValue(false);
            // Negative (shield only, out of feet reach) is ignored by queueSwallow. / 负值（仅遮挡）会被忽略。
            SeekerTaotieClient.queueSwallow(claim);
        }
    }
}
