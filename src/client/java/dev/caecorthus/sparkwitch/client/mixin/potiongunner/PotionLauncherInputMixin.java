package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Launcher left-click (client intent only; the server validates every shot). While the main hand holds the launcher:
 * <ul>
 *   <li>{@code doAttack} HEAD (no item in use): one fire request per press, then answer false, so the launcher never
 *   attacks or swings.</li>
 *   <li>{@code handleInputEvents}: while an item is in use (scoped) vanilla drains attack presses without calling
 *   {@code doAttack}; the {@code KeyBinding.wasPressed()} calls are wrapped (always called through, never redirected)
 *   and an attack press drained in that branch becomes the shot, so even a click shorter than a tick fires.</li>
 *   <li>{@code handleBlockBreaking} HEAD: always cancelled (no mining or swinging); a held attack fires only through
 *   an open latch.</li>
 * </ul>
 * The Seeker remote view and the Control Expert stun make the attack key read as released at {@code KeyBinding}, so
 * none of these paths sees a press during them, whatever the injector order.
 * 炮筒左键（仅为客户端意图；服务端复核每次发射）。主手持炮筒时：{@code doAttack} HEAD（未使用物品时）每次按键
 * 发出一次发射请求并返回 false，炮筒永远不会攻击或挥动；{@code handleInputEvents} 中，使用物品（开镜）期间原版会
 * 丢弃攻击按键而不调用 {@code doAttack}，因此包装 {@code KeyBinding.wasPressed()} 调用（始终继续调用，从不重定向），
 * 在该分支被丢弃的攻击按键即成为一次发射，短于一刻的点击也会生效；{@code handleBlockBreaking} HEAD 始终取消（不挖掘、
 * 不挥动），按住攻击仅在闩锁打开时发射。搜寻者遥控视角与控场专家眩晕会在 {@code KeyBinding} 层让攻击键读作未按下，
 * 因此无论注入顺序如何，这些路径在其期间都看不到按键。
 */
@Mixin(MinecraftClient.class)
public abstract class PotionLauncherInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fireLauncherOnAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PotionFireInput.onAttack((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean sparkwitch$fireLauncherOnDrainedPress(KeyBinding key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (pressed && key == client.options.attackKey) {
            PotionFireInput.onPressDrainedWhileUsing(client);
        }
        return pressed;
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fireLauncherWhileHeld(boolean breaking, CallbackInfo ci) {
        if (PotionFireInput.onBlockBreaking((MinecraftClient) (Object) this, breaking)) {
            ci.cancel();
        }
    }
}
