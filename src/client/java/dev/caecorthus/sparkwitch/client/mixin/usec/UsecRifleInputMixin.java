package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.usec.UsecFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * USEC rifle left-click (client intent only; the server validates every shot). A copy of the launcher's input seam
 * ({@code PotionLauncherInputMixin}), gated on the rifle instead. While the main hand holds the rifle:
 * <ul>
 *   <li>{@code handleInputEvents}: the {@code KeyBinding.wasPressed()} calls are wrapped (always called through, never
 *   redirected). An attack press drained by either vanilla read (the in-use drain while scoped, or the
 *   {@code doAttack} loop) is an edge for {@code UsecFireInput.onPressEdge}, the only fire path.</li>
 *   <li>{@code doAttack} HEAD: swallowed (answer false); the rifle never attacks or swings, and this never fires.</li>
 *   <li>{@code handleBlockBreaking} HEAD: always cancelled (no mining or swinging); a held key never fires.</li>
 * </ul>
 * The stun, Seeker and Kidnapper key locks make the attack key read as released at {@code KeyBinding}, so none of these
 * paths sees an edge during them, whatever the injector order. Each wrapper checks only its own item, so it chains
 * with the launcher and Death Ray seams on the same methods.
 * USEC 步枪左键（仅为客户端意图；服务端复核每次开火）。复制炮筒的输入接缝（{@code PotionLauncherInputMixin}），改为以步枪
 * 为条件。主手持步枪时：{@code handleInputEvents} 中包装 {@code KeyBinding.wasPressed()} 调用（始终继续调用，从不重定向），
 * 任一原版读取（开镜时的使用中丢弃循环或 {@code doAttack} 循环）取出的攻击按键都是 {@code UsecFireInput.onPressEdge} 的
 * 按下沿，也是唯一的发射路径；{@code doAttack} HEAD 吞掉攻击（返回 false），步枪不攻击也不挥动，且此处从不发射；
 * {@code handleBlockBreaking} HEAD 始终取消（不挖掘、不挥动），按住不放永不发射。眩晕、搜寻者与绑匪按键锁在
 * {@code KeyBinding} 层让攻击键读作未按下，因此无论注入顺序如何这些路径在其期间都看不到按下沿。每个包装只检查自己的物品，
 * 因此可与同一方法上的炮筒与死亡射线接缝串联。
 */
@Mixin(MinecraftClient.class)
public abstract class UsecRifleInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$swallowUsecRifleAttack(CallbackInfoReturnable<Boolean> cir) {
        if (UsecFireInput.onAttack((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"))
    private boolean sparkwitch$fireUsecRifleOnPressEdge(KeyBinding key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (pressed && key == client.options.attackKey) {
            UsecFireInput.onPressEdge(client);
        }
        return pressed;
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$cancelUsecRifleBlockBreaking(CallbackInfo ci) {
        if (UsecFireInput.onBlockBreaking((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
