package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.render.BlindEchoView;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the Blind's world depth at the head of {@code ParticleManager#renderParticles}. {@code WorldRenderer#render}
 * calls it once per frame in each graphics branch, after translucent terrain and the full entity flush, so glass and
 * Wathe's translucent hull and privacy panels are in the copy and particles are not. The method head is used instead
 * of the call site so a mod that redirects that call cannot make this injector fail; a no-op unless
 * {@code BlindEchoView#captureWorld} prepared this frame.
 * 在 {@code ParticleManager#renderParticles} 开头拷贝盲人的世界深度。{@code WorldRenderer#render} 每帧在各画质分支中于
 * 半透明地形与实体全部刷新之后调用它一次，因此拷贝包含玻璃以及 Wathe 的半透明船体与隐私玻璃板，而不含粒子。
 * 使用方法开头而非调用处，这样其他模组重定向该调用也不会让本注入失败；除非 {@code BlindEchoView#captureWorld} 已为本帧
 * 做好准备，否则不做任何事。
 */
@Mixin(ParticleManager.class)
public abstract class BlindDepthCaptureMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void sparkwitch$captureBlindDepth(LightmapTextureManager lightmapTextureManager, Camera camera,
                                              float tickDelta, CallbackInfo ci) {
        BlindEchoView.captureDepth();
    }
}
