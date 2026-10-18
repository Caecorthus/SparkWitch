package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.client.render.block_entity.AnimatableBlockEntityRenderer;
import dev.doctor4t.wathe.client.render.block_entity.SmallDoorBlockEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External Wathe seam: every Wathe small and train door is drawn by {@code SmallDoorBlockEntityRenderer} in a cutout
 * layer, and the glass doors' windows (and the steel doors' small window) are fully transparent texels, so they never
 * write depth and the Blind's line art showed the room behind them. While the Blind view is active the same door part
 * is drawn once more into vanilla's depth-only water-mask layer (no colour, no alpha discard; the layer boats use for
 * their water patch), so a closed door reads as a solid surface. Other players' frames are unchanged. The renderer
 * pushes no matrix of its own, so at TAIL the stack still holds its translate and flip, and the part keeps the angles
 * {@code setAngles} just gave it. The water mask is a fixed buffer flushed before the Blind's depth copy.
 * 外部 Wathe 接缝：Wathe 的所有小门与车门都由 {@code SmallDoorBlockEntityRenderer} 以镂空层绘制，玻璃门的窗（以及钢门的小窗）
 * 是全透明像素，从不写入深度，盲人的线稿因此会显示门后的房间。盲人视图生效期间，同一扇门的模型会再画一次到原版只写深度的
 * 水面遮罩层（无颜色、不按透明度丢弃；船用它挡住船内的水），使关着的门成为实心表面。其他玩家的画面不变。该渲染器不自行
 * push 矩阵，因此在 TAIL 处矩阵栈仍保留它的平移与翻转，模型部件也保留 {@code setAngles} 刚设置的角度。水面遮罩是固定缓冲，
 * 在盲人拷贝深度之前就已刷新。
 */
@Mixin(SmallDoorBlockEntityRenderer.class)
public abstract class BlindDoorDepthMixin {
    @Inject(method = "render(Ldev/doctor4t/wathe/block_entity/SmallDoorBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("TAIL"))
    private void sparkwitch$blindDoorDepth(SmallDoorBlockEntity entity, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, int overlay,
                                          CallbackInfo ci) {
        if (BlindClientGates.viewActive()) {
            ((AnimatableBlockEntityRenderer<?>) (Object) this).getPart()
                    .render(matrices, vertexConsumers.getBuffer(RenderLayer.getWaterMask()), light, overlay);
        }
    }
}
