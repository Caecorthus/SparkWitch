package dev.caecorthus.sparkwitch.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.render.entity.EntityModelCapture;
import dev.caecorthus.sparkwitch.client.render.entity.EntityRedraw;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entity render protocol hooks on {@code EntityRenderDispatcher#render} (2026-10-25): tracks the outermost dispatched
 * entity for {@link EntityModelCapture} (a whole-method wrap, so a renderer that throws still leaves it), and skips the
 * shadow, fire and hitbox while an {@link EntityRedraw} runs. ETF's own HEAD/RETURN state mount on the same method runs
 * inside the wrap; every other draw passes through unchanged.
 * {@code EntityRenderDispatcher#render} 上的实体渲染协议钩子（2026-10-25）：为 {@link EntityModelCapture} 记录最外层的分发实体
 * （包裹整个方法，渲染器抛出异常时也会退出），并在 {@link EntityRedraw} 执行期间跳过阴影、火焰与碰撞箱。ETF 在同一方法
 * HEAD/RETURN 的状态挂载在包裹内部执行；其他绘制原样通过。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderProtocolDispatcherMixin {
    private static final String RENDER = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/"
            + "MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @WrapMethod(method = RENDER)
    private void sparkwitch$trackDispatchedEntity(Entity entity, double x, double y, double z, float yaw,
                                                  float tickDelta, MatrixStack matrices,
                                                  VertexConsumerProvider vertexConsumers, int light,
                                                  Operation<Void> original) {
        EntityModelCapture.enterDispatch(entity);
        try {
            original.call(entity, x, y, z, yaw, tickDelta, matrices, vertexConsumers, light);
        } finally {
            EntityModelCapture.exitDispatch();
        }
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderFire("
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/entity/Entity;Lorg/joml/Quaternionf;)V"))
    private void sparkwitch$noFireInRedraw(EntityRenderDispatcher dispatcher, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, Entity entity,
                                          Quaternionf rotation, Operation<Void> original) {
        if (EntityRedraw.isActive()) {
            return;
        }
        original.call(dispatcher, matrices, vertexConsumers, entity, rotation);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderShadow("
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/entity/Entity;FFLnet/minecraft/world/WorldView;F)V"))
    private void sparkwitch$noShadowInRedraw(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            Entity entity, float opacity, float tickDelta, WorldView world,
                                            float radius, Operation<Void> original) {
        if (EntityRedraw.isActive()) {
            return;
        }
        original.call(matrices, vertexConsumers, entity, opacity, tickDelta, world, radius);
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitboxes:Z"))
    private boolean sparkwitch$noHitboxInRedraw(boolean renderHitboxes) {
        return renderHitboxes && !EntityRedraw.isActive();
    }
}
