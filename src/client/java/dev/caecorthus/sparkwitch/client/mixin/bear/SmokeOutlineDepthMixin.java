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
 * every outline layer ({@code RenderLayer#isOutline}, i.e. the entity-outline target) draws with {@code LEQUAL} depth
 * and depth writes, so the target's depth holds the nearest silhouette for the per-pixel smoke mask. Vanilla's
 * {@code ALWAYS} phase never enables the depth test, so outlines otherwise write no depth at all. The target's depth is
 * cleared at the start of each world render and never receives world depth, so walls still do not hide outlines.
 * Wraps only the upload call inside {@code RenderLayer#draw}, after the layer's phases are set and before they are
 * cleared; any other layer, or any pass without smoke, calls straight through.
 * 纯客户端：烟可能触及当前世界渲染时（{@code SmokeOutlineMask.capturingSilhouetteDepth()}），每个描边层
 * （{@code RenderLayer#isOutline}，即实体描边目标）以 {@code LEQUAL} 深度测试并开启深度写入绘制，使该目标的深度保存最近的剪影，
 * 供逐像素烟雾遮罩使用。原版 {@code ALWAYS} 阶段从不启用深度测试，否则描边根本不写深度。该目标的深度在每次世界渲染开始时
 * 清除且从不接收世界深度，因此墙体仍不遮挡描边。只包装 {@code RenderLayer#draw} 内的上传调用（图层各阶段已设置、尚未清除）；
 * 其他图层或无烟的渲染直接调用原方法。
 */
@Mixin(RenderLayer.class)
public abstract class SmokeOutlineDepthMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V"))
    private void sparkwitch$keepSilhouetteDepth(BuiltBuffer buffer, Operation<Void> original) {
        if (SmokeOutlineMask.capturingSilhouetteDepth() && ((RenderLayer) (Object) this).isOutline()) {
            SmokeOutlineMask.drawWithSilhouetteDepth(() -> original.call(buffer));
        } else {
            original.call(buffer);
        }
    }
}
