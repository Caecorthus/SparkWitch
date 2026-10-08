package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopePictureInPicture;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The PiP lens pass skips particles: {@code renderParticles} HEAD is cancelled while {@code ScopePictureInPicture}
 * renders the lens, and only then. Particles are only drawn here, never ticked or spawned, so the main view's particles
 * are unaffected. Client-only presentation.
 * 画中画镜内渲染不画粒子：仅在 {@code ScopePictureInPicture} 渲染镜内时取消 {@code renderParticles} 的 HEAD。粒子在这里只被
 * 绘制、从不被更新或生成，因此主画面的粒子不受影响。纯客户端展示。
 */
@Mixin(ParticleManager.class)
public abstract class ScopeParticleManagerMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$skipLensParticles(CallbackInfo ci) {
        if (ScopePictureInPicture.isRenderingLens()) {
            ci.cancel();
        }
    }
}
