package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Identity hardening for other players drawn into the Blind's frame (D4, plus the brief's feature suppression):
 * <ul>
 *   <li>every feature renderer (held items, armor, capes, elytra, head items, arrows, parrots, mod features) is skipped
 *   on other players, so the line art never shows a knife or gun shape; chains with Wathe's psycho and SparkTraits'
 *   Chameleon wraps on the same call (all of them only skip);</li>
 *   <li>D4: the single {@code isVisible} read returns true for a perceived, non-spectator, non-Wraith other player, so
 *   an invisible player who makes sound still draws its body, only into the Blind's own frame;</li>
 *   <li>D4: such a forced player always draws its normal opaque layer at full alpha. These two outermost wraps call
 *   {@code getRenderLayer} and the model's {@code render} directly for it, so inner wraps on the same calls (today only
 *   SparkTraits' Chameleon fade, which would switch to the translucent layer and fade the alpha to 0, leaving nothing
 *   in the captured depth) do not run for that player; every other entity passes through unchanged;</li>
 *   <li>no name label on other players.</li>
 * </ul>
 * 进入盲人画面的其他玩家的身份加固（D4 及简报要求的附加层抑制）：其他玩家的所有附加层渲染器（手持物、护甲、披风、
 * 鞘翅、头部物品、箭矢、鹦鹉、模组附加层）一律跳过，线稿因此不会露出刀枪轮廓，并与 Wathe 疯魔与 SparkTraits
 * 变色龙在同一调用上的包裹串联（它们都只做跳过）；D4：对被感知、非旁观者、非冤魂的其他玩家，唯一一次
 * {@code isVisible} 读取返回真，因此发出声音的隐身玩家仍会画出身体，且只在盲人自己的画面中；D4：这类被强制显示的玩家
 * 始终以普通不透明渲染层、满不透明度绘制：这两个最外层包裹为其直接调用 {@code getRenderLayer} 与模型的
 * {@code render}，因此同一调用上的内层包裹（目前只有 SparkTraits 变色龙渐隐，它会切换到半透明层并把透明度降到 0，
 * 使捕获的深度中什么都没有）不会对该玩家执行；其他实体原样通过；其他玩家不显示名字标签。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 2000)
public abstract class BlindGateLivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected abstract @Nullable RenderLayer getRenderLayer(T entity, boolean showBody, boolean translucent,
                                                            boolean showOutline);

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void sparkwitch$blindSkipsOtherPlayerFeatures(FeatureRenderer<T, M> feature, MatrixStack matrices,
                                                          VertexConsumerProvider vertexConsumers, int light,
                                                          Entity entity, float limbAngle, float limbDistance,
                                                          float tickDelta, float animationProgress, float headYaw,
                                                          float headPitch, Operation<Void> original) {
        if (BlindClientGates.suppressesFeatures(entity)) {
            return;
        }
        original.call(feature, matrices, vertexConsumers, light, entity, limbAngle, limbDistance, tickDelta,
                animationProgress, headYaw, headPitch);
    }

    @ModifyExpressionValue(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;isVisible(Lnet/minecraft/entity/LivingEntity;)Z")
    )
    private boolean sparkwitch$blindDrawsPerceivedInvisiblePlayers(boolean visible,
                                                                  @Local(argsOnly = true) LivingEntity entity) {
        return visible || BlindClientGates.forcesVisible(entity);
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/entity/LivingEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;")
    )
    private @Nullable RenderLayer sparkwitch$blindPerceivedPlayersUseTheOpaqueLayer(
            LivingEntityRenderer<T, M> renderer, T entity, boolean showBody, boolean translucent, boolean showOutline,
            Operation<RenderLayer> original) {
        if (BlindClientGates.forcesVisible(entity)) {
            return getRenderLayer(entity, true, false, showOutline);
        }
        return original.call(renderer, entity, showBody, translucent, showOutline);
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V")
    )
    private void sparkwitch$blindPerceivedPlayersDrawOpaque(EntityModel<T> model, MatrixStack matrices,
                                                           VertexConsumer vertices, int light, int overlay, int color,
                                                           Operation<Void> original,
                                                           @Local(argsOnly = true) LivingEntity entity) {
        if (BlindClientGates.forcesVisible(entity)) {
            model.render(matrices, vertices, light, overlay, color | 0xFF000000);
            return;
        }
        original.call(model, matrices, vertices, light, overlay, color);
    }

    @WrapMethod(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z")
    private boolean sparkwitch$blindHidesOtherPlayerLabels(T entity, Operation<Boolean> original) {
        if (BlindClientGates.suppressesFeatures(entity)) {
            return false;
        }
        return original.call(entity);
    }
}
