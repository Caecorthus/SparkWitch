package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32FireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M32A1 left-click (client intent only; the server validates every shot), the anti-tank launcher's
 * {@code PotionLauncherInputMixin} scheme for the M32A1. While the main hand holds it: every {@code wasPressed()} read
 * in {@code handleInputEvents} is wrapped (always called through), and an attack press drained by either branch is an
 * edge for {@code M32FireInput.onPressEdge}, the only fire path; {@code doAttack} HEAD swallows the attack (answer
 * false, no swing); {@code handleBlockBreaking} HEAD is always cancelled, so a held key never mines or fires. The
 * Seeker remote view, the Control Expert stun and the Kidnapper control make the attack key read as released at
 * {@code KeyBinding}, so no edge reaches this path during them, whatever the injector order. Each hook answers only for
 * the M32A1 and chains with the launcher's, the AXMC's and the Death Ray's hooks on the same calls.
 * M32A1 左键（仅为客户端意图；服务端复核每次发射），即反坦克炮筒 {@code PotionLauncherInputMixin} 的方案用于 M32A1。主手持它时：
 * 包装 {@code handleInputEvents} 中每次 {@code wasPressed()} 读取（始终继续调用），任一分支取出的攻击按键都是
 * {@code M32FireInput.onPressEdge} 的按下沿，也是唯一的发射路径；{@code doAttack} HEAD 吞掉攻击（返回 false，不挥动）；
 * {@code handleBlockBreaking} HEAD 始终取消，因此按住不放永远不会挖掘或发射。搜寻者遥控视角、控场专家眩晕与绑匪控制会在
 * {@code KeyBinding} 层让攻击键读作未按下，因此无论注入顺序如何，这些期间都不会有按下沿到达本路径。每个钩子只对 M32A1 作答，并与
 * 同一调用上的炮筒、AXMC 与死亡射线钩子串联。
 */
@Mixin(MinecraftClient.class)
public abstract class M32InputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$swallowM32Attack(CallbackInfoReturnable<Boolean> cir) {
        if (M32FireInput.onAttack((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean sparkwitch$fireM32OnPressEdge(KeyBinding key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (pressed && key == client.options.attackKey) {
            M32FireInput.onPressEdge(client);
        }
        return pressed;
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$cancelM32BlockBreaking(CallbackInfo ci) {
        if (M32FireInput.onBlockBreaking((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
