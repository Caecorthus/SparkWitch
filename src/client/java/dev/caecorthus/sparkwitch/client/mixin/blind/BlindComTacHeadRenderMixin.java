package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.BlindComTacHeadVisibility;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code HeadFeatureRenderer} is the only renderer that draws a non-{@code ArmorItem} head {@code Equipment}; it renders
 * nothing but the head-slot item, so cancelling it before its matrix push hides exactly a worn ComTac during a round
 * ({@link BlindComTacHeadVisibility}). Presentation only.
 * {@code HeadFeatureRenderer} 是唯一绘制非 {@code ArmorItem} 头部 {@code Equipment} 的渲染器，且只绘制头部槽物品；在其
 * 矩阵 push 之前取消，恰好只在对局中隐藏戴着的 ComTac（{@link BlindComTacHeadVisibility}）。仅为展示。
 */
@Mixin(HeadFeatureRenderer.class)
public abstract class BlindComTacHeadRenderMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideWornComTac(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (BlindComTacHeadVisibility.hidesHeadItem(entity)) {
            ci.cancel();
        }
    }
}
