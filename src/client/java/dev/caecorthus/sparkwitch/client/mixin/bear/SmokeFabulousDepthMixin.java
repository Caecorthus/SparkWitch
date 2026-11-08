package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.render.SmokeRenderer;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabulous! only: runs the BEAR smoke march at the head of {@code ParticleManager#renderParticles}, while the main
 * depth still holds opaque terrain and every entity (Fabulous! draws translucent terrain into its own target, and its
 * composite clears main's depth before {@code WorldRenderEvents.LAST}). The march samples that depth from its own
 * framebuffer, so nothing is copied. Method head rather than the call site, so a mod that redirects the call cannot
 * make this injector fail; a no-op unless {@code SmokeRenderer#beginWorldPass} chose this point. Client-only
 * presentation.
 * 仅 Fabulous!：在 {@code ParticleManager#renderParticles} 开头执行 BEAR 烟雾步进，此时主深度仍是不透明地形与全部实体
 * （Fabulous! 把半透明地形画进自己的目标，其合成会在 {@code WorldRenderEvents.LAST} 之前清掉主深度）。步进在自己的帧缓冲中
 * 采样该深度，因此不做任何拷贝。使用方法开头而非调用处，其他模组重定向该调用也不会让本注入失败；除非
 * {@code SmokeRenderer#beginWorldPass} 选中此处，否则不做任何事。纯客户端展示。
 */
@Mixin(ParticleManager.class)
public abstract class SmokeFabulousDepthMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void sparkwitch$captureSmokeDepth(LightmapTextureManager lightmapTextureManager, Camera camera,
                                              float tickDelta, CallbackInfo ci) {
        SmokeRenderer.beforeParticles();
    }
}
