package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetPsychoMask;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only: Wathe's psycho read answers 0 for a Magician puppet's stand-in while its look is resolved
 * ({@link MagicianPuppetPsychoMask}), so Wathe's own {@code getTexture} psycho HEAD skips the puppet. Matches only the
 * one client stand-in component by identity, so server-side and every other psycho read are unchanged.
 * 仅客户端：解析魔术师皮套外观期间，Wathe 对其替身的疯魔读取返回 0（{@link MagicianPuppetPsychoMask}），使 Wathe 自身
 * {@code getTexture} 的疯魔 HEAD 不作用于皮套。仅按身份匹配该客户端替身的组件，服务端与其他所有疯魔读取均不变。
 */
@Mixin(value = PlayerPsychoComponent.class, remap = false)
public abstract class MagicianPuppetPsychoMaskMixin {
    @ModifyReturnValue(method = "getPsychoTicks", at = @At("RETURN"))
    private int sparkwitch$noPsychoOnPuppetLook(int psychoTicks) {
        return psychoTicks > 0 && MagicianPuppetPsychoMask.masks(this) ? 0 : psychoTicks;
    }
}
