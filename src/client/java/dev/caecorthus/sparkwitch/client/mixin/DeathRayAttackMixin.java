package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.DeathRayClientHooks;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures Death Ray shots before vanilla left-click attacks resolve.
 * 在原版左键攻击结算前捕获死亡射线发射。
 */
@Mixin(MinecraftClient.class)
public abstract class DeathRayAttackMixin {
    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fireDeathRay(CallbackInfoReturnable<Boolean> cir) {
        if (DeathRayClientHooks.tryFire((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
