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
 * Fabulous! only: copies the main depth for the BEAR smoke at the head of {@code ParticleManager#renderParticles}.
 * {@code WorldRenderer#render} calls it once per frame after the full entity flush, and in Fabulous! the translucent
 * terrain has gone to its own target, so main still holds opaque terrain and every entity; the Fabulous! composite
 * clears that depth before {@code WorldRenderEvents.LAST}. Method head rather than the call site, so a mod that
 * redirects the call cannot make this injector fail; a no-op unless {@code SmokeRenderer#beginWorldPass} asked for a
 * copy this world render. Client-only presentation.
 * 仅 Fabulous!：在 {@code ParticleManager#renderParticles} 开头为 BEAR 烟雾拷贝主深度。{@code WorldRenderer#render} 每帧在
 * 实体全部刷新之后调用它一次；Fabulous! 下半透明地形已画进自己的目标，因此主深度此时仍是不透明地形与全部实体；
 * Fabulous! 合成会在 {@code WorldRenderEvents.LAST} 之前清掉该深度。使用方法开头而非调用处，其他模组重定向该调用也不会让
 * 本注入失败；除非 {@code SmokeRenderer#beginWorldPass} 要求本次世界渲染拷贝，否则不做任何事。纯客户端展示。
 */
@Mixin(ParticleManager.class)
public abstract class SmokeFabulousDepthMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void sparkwitch$captureSmokeDepth(LightmapTextureManager lightmapTextureManager, Camera camera,
                                              float tickDelta, CallbackInfo ci) {
        SmokeRenderer.captureFabulousDepth();
    }
}
