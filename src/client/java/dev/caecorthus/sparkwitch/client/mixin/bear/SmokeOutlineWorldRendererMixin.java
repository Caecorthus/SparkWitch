package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeOutlineMask;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only seams of the BEAR smoke outline mask ({@code SmokeOutlineMask}), once per {@code WorldRenderer#render}
 * call, so the scope picture-in-picture pass is masked with its own camera and projection:
 * <ul>
 *   <li>{@code render} HEAD (read-only, like Fabric's own HEAD hook): remembers the pass's camera and matrices and
 *   arms the mask only when smoke can touch the pass.</li>
 *   <li>around ordinal 0 of {@code PostEffectProcessor#render(F)} in {@code render}: the entity_outline chain (ordinal
 *   1 is Fabulous! transparency). Before it, the occlusion texture is marched and the silhouette depth (which the chain
 *   clears) is copied; after it, the outline target's smoke rectangle is rewritten with masked alpha. Both sit inside
 *   vanilla's "any outline drawn" branch, so frames without outlines do no work, and vanilla re-binds the main target
 *   right after.</li>
 *   <li>{@code render} RETURN: disarms.</li>
 * </ul>
 * Non-cancellable {@code @Inject}s without captured locals, so Wathe's {@code @Local} and NoellesRoles'
 * {@code CAPTURE_FAILHARD} in the same method are unaffected; nothing runs without smoke.
 * BEAR 烟雾描边遮罩（{@code SmokeOutlineMask}）的纯客户端接缝，每次 {@code WorldRenderer#render} 调用各一次，因此瞄具画中画
 * 渲染以自己的相机与投影遮罩：{@code render} HEAD（只读，与 Fabric 自己的 HEAD 钩子相同）记录本次渲染的相机与矩阵，烟可能
 * 触及时才进入就绪状态；{@code render} 中第 0 个 {@code PostEffectProcessor#render(F)} 调用前后：即 entity_outline 后处理
 * （第 1 个是“极佳”画质的透明度处理）。之前步进生成遮挡纹理并复制剪影深度（后处理会清除它），之后以遮罩后的 alpha 重写
 * 描边目标的烟雾矩形；两者都在原版“画过描边”的分支内，无描边的帧不做任何工作，原版随后立即重新绑定主目标；
 * {@code render} RETURN 解除就绪。均为不可取消且不捕获局部变量的 {@code @Inject}，不影响同一方法中 Wathe 的 {@code @Local}
 * 与 NoellesRoles 的 {@code CAPTURE_FAILHARD}；无烟时什么都不执行。
 */
@Mixin(WorldRenderer.class)
public abstract class SmokeOutlineWorldRendererMixin {
    private static final String POST_EFFECT_RENDER = "Lnet/minecraft/client/gl/PostEffectProcessor;render(F)V";

    @Shadow
    @Nullable
    public abstract Framebuffer getEntityOutlinesFramebuffer();

    @Inject(method = "render", at = @At("HEAD"))
    private void sparkwitch$beginSmokeOutlinePass(RenderTickCounter tickCounter, boolean renderBlockOutline,
                                                  Camera camera, GameRenderer gameRenderer,
                                                  LightmapTextureManager lightmapTextureManager,
                                                  Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                                  CallbackInfo ci) {
        SmokeOutlineMask.beginPass(camera, positionMatrix, projectionMatrix);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = POST_EFFECT_RENDER, ordinal = 0))
    private void sparkwitch$beforeOutlinePost(CallbackInfo ci) {
        SmokeOutlineMask.beforeOutlinePost(getEntityOutlinesFramebuffer());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = POST_EFFECT_RENDER, ordinal = 0,
            shift = At.Shift.AFTER))
    private void sparkwitch$afterOutlinePost(CallbackInfo ci) {
        SmokeOutlineMask.afterOutlinePost(getEntityOutlinesFramebuffer());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sparkwitch$endSmokeOutlinePass(CallbackInfo ci) {
        SmokeOutlineMask.endPass();
    }
}
