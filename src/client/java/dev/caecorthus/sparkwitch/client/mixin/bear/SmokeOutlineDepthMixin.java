package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeOutlineMask;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only: while smoke can touch the current world pass ({@code SmokeOutlineMask.capturingSilhouetteDepth()}),
 * every outline layer ({@code RenderLayer#isOutline}, i.e. the entity-outline target) is bounded on screen first
 * ({@code SmokeOutlineMask.noteOutlineDraw}, so the mask only covers the smoke where outlines are) and draws with
 * {@code LEQUAL} depth and depth writes into the target's depth attachment, which the mask swaps for its own texture
 * for the pass, so it holds the nearest silhouette for the per-pixel smoke mask. Vanilla's {@code ALWAYS} phase never
 * enables the depth test, so outlines otherwise write no depth at all. That depth is cleared on the pass's first
 * outline draw and never receives world depth, so walls still do not hide outlines. Wraps only the upload call inside
 * {@code RenderLayer#draw}, after the layer's phases are set and before they are cleared; any other layer, or any pass
 * without smoke, calls straight through.
 * 纯客户端：烟可能触及当前世界渲染时（{@code SmokeOutlineMask.capturingSilhouetteDepth()}），每个描边层
 * （{@code RenderLayer#isOutline}，即实体描边目标）先在屏幕上求出边界（{@code SmokeOutlineMask.noteOutlineDraw}，使遮罩只覆盖
 * 烟中有描边之处），再以 {@code LEQUAL} 深度测试并开启深度写入绘制到目标的深度附件上；遮罩在本次渲染中把该附件换成自己的
 * 纹理，因此它保存最近的剪影，供逐像素烟雾遮罩使用。原版 {@code ALWAYS} 阶段从不启用深度测试，否则描边根本不写深度。该深度在
 * 本次渲染第一次描边绘制时清除且从不接收世界深度，因此墙体仍不遮挡描边。只包装 {@code RenderLayer#draw} 内的上传调用（图层
 * 各阶段已设置、尚未清除）；其他图层或无烟的渲染直接调用原方法。
 */
@Mixin(RenderLayer.class)
public abstract class SmokeOutlineDepthMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V"))
    private void sparkwitch$keepSilhouetteDepth(BuiltBuffer buffer, Operation<Void> original) {
        if (SmokeOutlineMask.capturingSilhouetteDepth() && ((RenderLayer) (Object) this).isOutline()) {
            SmokeOutlineMask.noteOutlineDraw(buffer);
            SmokeOutlineMask.drawWithSilhouetteDepth(() -> original.call(buffer));
        } else {
            original.call(buffer);
        }
    }
}
