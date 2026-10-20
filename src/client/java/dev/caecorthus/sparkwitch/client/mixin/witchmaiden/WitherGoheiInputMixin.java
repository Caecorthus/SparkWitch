package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.witchmaiden.WitherGoheiFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wither Gohei left-click: swallows the vanilla attack and block breaking while the Gohei is held, and fires on each
 * drained attack press (Potion Gunner launcher pattern).
 * 凋灵御币左键：手持御币时吞掉原版攻击与挖掘，并在每次被取出的攻击按键时射击（沿用药炮筒的做法）。
 */
@Mixin(MinecraftClient.class)
public abstract class WitherGoheiInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$swallowGoheiAttack(CallbackInfoReturnable<Boolean> cir) {
        if (WitherGoheiFireInput.onAttack((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean sparkwitch$fireGoheiOnPressEdge(KeyBinding key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (pressed && key == client.options.attackKey) {
            WitherGoheiFireInput.onPressEdge(client);
        }
        return pressed;
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$cancelGoheiBlockBreaking(CallbackInfo ci) {
        if (WitherGoheiFireInput.onBlockBreaking((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
