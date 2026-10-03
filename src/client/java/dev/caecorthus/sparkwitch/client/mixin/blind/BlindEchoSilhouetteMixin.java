package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.blind.render.BlindSilhouettePass;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only during the Blind's private silhouette re-render ({@link BlindSilhouettePass#isRendering()}): skips every feature
 * renderer (held items, armor, capes, mod features) and the name label, so a through-wall silhouette is a bare body
 * and never shows a weapon shape or a name. Outside that pass both handlers call through unchanged.
 * 仅在盲人私有的轮廓重渲染期间（{@link BlindSilhouettePass#isRendering()}）：跳过所有特征渲染器（手持物、盔甲、披风、
 * 模组特征）与名字标签，使穿墙轮廓只是光身体，不会露出武器形状或名字。该 pass 之外两个处理器都原样放行。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class BlindEchoSilhouetteMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;"
                    + "render(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void sparkwitch$skipFeaturesInBlindSilhouette(
            FeatureRenderer<?, ?> feature,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            Entity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch,
            Operation<Void> original
    ) {
        if (BlindSilhouettePass.isRendering()) {
            return;
        }
        original.call(feature, matrices, vertexConsumers, light, entity, limbAngle, limbDistance, tickDelta,
                animationProgress, headYaw, headPitch);
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideLabelInBlindSilhouette(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (BlindSilhouettePass.isRendering()) {
            cir.setReturnValue(false);
        }
    }
}
