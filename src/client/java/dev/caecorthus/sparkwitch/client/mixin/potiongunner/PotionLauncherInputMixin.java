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
 *   <li>{@code handleInputEvents}: the {@code KeyBinding.wasPressed()} calls are wrapped (always called through, never
 *   redirected). Vanilla reads the attack key there twice, the drain while an item is in use (scoped) and the
 *   {@code doAttack} loop; an attack press taken by either is an edge for {@code PotionFireInput.onPressEdge}, the
 *   only fire path, so even a click shorter than a tick fires. The in-use branch calls {@code stopUsingItem} before it
 *   drains, so the edge is not gated on {@code isUsingItem()}.</li>
 *   <li>{@code doAttack} HEAD: swallowed (answer false), so the launcher never attacks or swings. Never fires.</li>
 *   <li>{@code handleBlockBreaking} HEAD: always cancelled (no mining or swinging). Never fires, so a held key never
 *   shoots (D-R3; the latch also ignores keyboard auto-repeat edges).</li>
 * </ul>
 * The Seeker remote view and the Control Expert stun (HEAD, zeroing the queue) and the Kidnapper control (RETURN,
 * consuming one queued press per read) make the attack key read as released at {@code KeyBinding}, so none of these
 * paths sees an edge during them, whatever the injector order.
 * 炮筒左键（仅为客户端意图；服务端复核每次发射）。主手持炮筒时：{@code handleInputEvents} 中包装
 * {@code KeyBinding.wasPressed()} 调用（始终继续调用，从不重定向）。原版在其中两次读取攻击键：使用物品（开镜）时的
 * 丢弃循环与 {@code doAttack} 循环；任一处取出的攻击按键都是 {@code PotionFireInput.onPressEdge} 的按下沿，也是唯一的
 * 发射路径，短于一刻的点击也会生效。使用中分支会先调用 {@code stopUsingItem} 再取出按键，因此按下沿不以
 * {@code isUsingItem()} 为条件。{@code doAttack} HEAD 吞掉攻击（返回 false），炮筒永远不会攻击或挥动，也从不发射。
 * {@code handleBlockBreaking} HEAD 始终取消（不挖掘、不挥动），从不发射，因此按住不放永远不会开火（D-R3；闩锁还会
 * 忽略键盘自动重复产生的按下沿）。搜寻者遥控视角与控场专家眩晕（HEAD，清零排队）以及绑匪控制（RETURN，每次读取消耗
 * 一个排队按键）会在 {@code KeyBinding} 层让攻击键读作未按下，因此无论注入顺序如何，这些路径在其期间都看不到按下沿。
 */
@Mixin(MinecraftClient.class)
public abstract class PotionLauncherInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$swallowLauncherAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PotionFireInput.onAttack((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean sparkwitch$fireLauncherOnPressEdge(KeyBinding key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (pressed && key == client.options.attackKey) {
            PotionFireInput.onPressEdge(client);
        }
        return pressed;
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$cancelLauncherBlockBreaking(CallbackInfo ci) {
        if (PotionFireInput.onBlockBreaking((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
