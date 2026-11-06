package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.helmet.RysTHeadVisibility;
import dev.caecorthus.sparkwitch.client.bear.helmet.RysTWornCracks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The worn Rys-T on vanilla {@code HeadFeatureRenderer}, the only renderer of a non-{@code ArmorItem} head item, which
 * draws nothing but the head-slot item. HEAD (cancellable, before its matrix push): D7 skips the helmet on a wearer
 * this viewer cannot see ({@link RysTHeadVisibility}). After its single {@code HeldItemRenderer#renderItem} call, still
 * inside the head matrix: the bullet holes on the glass ({@link RysTWornCracks}). Both act only on a worn Rys-T; every
 * other head item, the Blind ComTac hook and the entity render protocol are untouched (no model part is written, no
 * entity is drawn again). Client presentation only.
 * 原版 {@code HeadFeatureRenderer} 上的穿戴 Rys-T；它是唯一绘制非 {@code ArmorItem} 头部物品的渲染器，且只绘制头部槽物品。
 * HEAD（可取消，在其矩阵 push 之前）：D7 在观察者看不到的佩戴者身上跳过头盔（{@link RysTHeadVisibility}）。在其唯一一次
 * {@code HeldItemRenderer#renderItem} 调用之后、仍处于头部矩阵中：绘制玻璃上的弹孔（{@link RysTWornCracks}）。两者只作用于
 * 穿戴的 Rys-T；其他头部物品、盲人 ComTac 钩子与实体渲染协议均不受影响（不写模型部件，不重复绘制实体）。仅客户端展示。
 */
@Mixin(HeadFeatureRenderer.class)
public abstract class RysTHeadFeatureMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideRysTOnUnseenWearer(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                   int light, LivingEntity entity, float limbAngle,
                                                   float limbDistance, float tickDelta, float animationProgress,
                                                   float headYaw, float headPitch, CallbackInfo ci) {
        if (RysTHeadVisibility.hidesHelmet(entity)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void sparkwitch$drawRysTWornCracks(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                               int light, LivingEntity entity, float limbAngle, float limbDistance,
                                               float tickDelta, float animationProgress, float headYaw,
                                               float headPitch, CallbackInfo ci) {
        RysTWornCracks.render(matrices, vertexConsumers, light, entity);
    }
}
