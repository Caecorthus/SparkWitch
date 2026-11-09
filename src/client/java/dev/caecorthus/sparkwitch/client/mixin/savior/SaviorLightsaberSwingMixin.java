package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorLightsaberAnimation;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberSwingC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the swing intent from the client's own {@code doAttack} call, so air swings, block hits and entity hits all
 * produce it exactly when vanilla resolves an attack. Only a lit lightsaber in the main hand qualifies; the server
 * re-validates before broadcasting. Holding the attack key repeats it at the attack rate, matching the animation.
 * 从客户端自己的 {@code doAttack} 发送挥动意图，因此空挥、打方块和打实体都会在原版结算攻击时触发。只有主手且已点亮的
 * 光剑才符合；服务端会再次校验后广播。按住攻击键时按攻击速率重复，与挥动动画一致。
 */
@Mixin(MinecraftClient.class)
public abstract class SaviorLightsaberSwingMixin {
    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$playLightsaberSwing(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        ClientPlayerEntity player = client.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())
                || !SaviorLightsaberAnimation.isBladeExtended(stack)) {
            return;
        }
        // Item cooldown (45 s start / 30 s after a kill) blocks the left click entirely, animation included.
        // Creative mode ignores it. / 物品冷却（开局 45 秒/击杀后 30 秒）期间彻底封锁左键，包括挥手动画；创造模式忽略。
        if (!player.isCreative() && player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            cir.setReturnValue(false);
            return;
        }
        ClientPlayNetworking.send(new SaviorLightsaberSwingC2SPayload());
    }
}
