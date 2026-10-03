package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers armor drawn outside the feature loop (SparkTraits' Pig body draws its helmet through an invoker on this
 * private method): while the Blind view is active no armor piece renders on another player. Presentation only.
 * 覆盖在附加层循环之外绘制的护甲（SparkTraits 的猪身体通过调用器直接调用此私有方法绘制头盔）：盲人视图生效期间，
 * 其他玩家身上不渲染任何护甲。仅为展示。
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class BlindGateArmorFeatureRendererMixin {
    @Inject(
            method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$blindSkipsOtherPlayerArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                       LivingEntity entity, EquipmentSlot slot, int light,
                                                       BipedEntityModel<?> model, CallbackInfo ci) {
        if (BlindClientGates.suppressesFeatures(entity)) {
            ci.cancel();
        }
    }
}
