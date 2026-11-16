package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorLightsaberAnimation;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberSwingC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side lightsaber left click, in two parts. HEAD: the item cooldown (45 s start / 30 s after a kill) blocks the
 * whole left click, swing animation included; creative mode ignores it. The swing intent is sent only where vanilla
 * really swings, at the {@code swingHand} call inside {@code doAttack}: after vanilla's own attack-cooldown, missing
 * target, riding and disabled-item returns, and never when another HEAD cancel on {@code doAttack} (Rift session,
 * Seeker remote, Raven Form crow, Death Ray and the rest) dropped the click. Air swings, block hits and entity hits all
 * reach that call, and holding the attack key repeats it at the attack rate, matching the animation. Only a lit
 * lightsaber in the main hand qualifies; the server re-validates before broadcasting the swing sound.
 * 客户端光剑左键，分两部分。HEAD：物品冷却（开局 45 秒/击杀后 30 秒）期间封锁整个左键，包括挥手动画；创造模式忽略。挥动
 * 意图只在原版真正挥手的位置发送，即 {@code doAttack} 内对 {@code swingHand} 的调用：位于原版攻击冷却、无准星目标、骑乘与
 * 物品禁用等提前返回之后；{@code doAttack} 上其他 HEAD 取消（裂隙会话、搜寻者遥控、化鸦乌鸦、死光等）吞掉这次点击时绝不
 * 发送。空挥、打方块与打实体都会走到该调用；按住攻击键时按攻击速率重复，与挥动动画一致。只有主手且已点亮的光剑才符合；
 * 服务端会再次校验后才广播挥动音效。
 */
@Mixin(MinecraftClient.class)
public abstract class SaviorLightsaberSwingMixin {
    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockCoolingLightsaber(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = sparkwitch$litLightsaber((MinecraftClient) (Object) this);
        ClientPlayerEntity player = ((MinecraftClient) (Object) this).player;
        if (stack != null && player != null && !player.isCreative()
                && player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "doAttack()Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"
            )
    )
    private void sparkwitch$sendLightsaberSwing(CallbackInfoReturnable<Boolean> cir) {
        if (sparkwitch$litLightsaber((MinecraftClient) (Object) this) != null
                && ClientPlayNetworking.canSend(SaviorLightsaberSwingC2SPayload.ID)) {
            ClientPlayNetworking.send(new SaviorLightsaberSwingC2SPayload());
        }
    }

    /** The lit lightsaber in a non-spectator local player's main hand, or null. / 非旁观本地玩家主手中已点亮的光剑，否则为 null。 */
    @Unique
    private static ItemStack sparkwitch$litLightsaber(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isSpectator()) {
            return null;
        }
        ItemStack stack = player.getMainHandStack();
        return stack.isOf(SparkWitchItems.saviorLightsaber()) && SaviorLightsaberAnimation.isBladeExtended(stack)
                ? stack
                : null;
    }
}
