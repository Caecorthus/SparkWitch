package dev.caecorthus.sparkwitch.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.render.entity.EntityModelCapture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entity render protocol hook (2026-10-25): hands the main body model's single draw in {@code LivingEntityRenderer#render}
 * the consumer {@link EntityModelCapture#tee} picks, so an open recorder gets exactly what the world pass draws.
 * Priority 2100 keeps this outside {@code BlindGateLivingEntityRendererMixin} (2000), which draws a Blind-perceived
 * player's model itself and skips inner wraps; it passes the consumer it received on, so both see the recording.
 * 实体渲染协议钩子（2026-10-25）：让 {@code LivingEntityRenderer#render} 中主身体模型唯一的一次绘制使用
 * {@link EntityModelCapture#tee} 选出的消费者，使当前录制器拿到世界 pass 实际画出的内容。优先级 2100 使其位于
 * {@code BlindGateLivingEntityRendererMixin}（2000）之外；后者会自行绘制被盲人感知玩家的模型并跳过内层包裹，但会把收到的
 * 消费者传下去，因此两者都能看到录制。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 2100)
public abstract class EntityModelCaptureMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render("
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void sparkwitch$recordBodyModel(EntityModel<T> model, MatrixStack matrices, VertexConsumer vertices,
                                            int light, int overlay, int color, Operation<Void> original) {
        original.call(model, matrices, EntityModelCapture.tee(vertices), light, overlay, color);
    }
}
