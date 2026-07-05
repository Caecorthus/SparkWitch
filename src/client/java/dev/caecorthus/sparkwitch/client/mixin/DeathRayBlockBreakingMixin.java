package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hooks.DeathRayClientHooks;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures Death Ray shots from Minecraft's held-left-click block-breaking path.
 * 从 Minecraft 持续左键挖掘路径捕获死亡射线发射，避免没有进入 doAttack 时漏发。
 */
@Mixin(MinecraftClient.class)
public abstract class DeathRayBlockBreakingMixin {
    @Inject(method = "handleBlockBreaking(Z)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fireDeathRayFromHeldAttack(boolean breaking, CallbackInfo ci) {
        if (breaking && DeathRayClientHooks.tryFire((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
