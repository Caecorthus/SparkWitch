package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.client.hooks.KidnapperThrowClientHooks;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Claims eligible Kidnapper throws before vanilla resolves any use target or hand. / 在原版解析使用目标或手之前认领合格的绑架者投掷。 */
@Mixin(MinecraftClient.class)
public abstract class KidnapperThrowUseMixin {
    @Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$throwKidnapperBody(CallbackInfo ci) {
        if (KidnapperThrowClientHooks.tryThrow((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
