package dev.caecorthus.sparkwitch.client.mixin.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecScopeLensAnchors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the item matrix right after vanilla's only {@code translate(-0.5, -0.5, -0.5)} in the model-taking
 * {@code renderItem}, when the display transform is applied and model units map straight onto the quads. That is
 * before Fabric Indigo's {@code isBuiltin()} hook, which can take over the draw. Reads only. Presentation only.
 * 在接收模型的 {@code renderItem} 中原版唯一一次 {@code translate(-0.5, -0.5, -0.5)} 之后读取物品矩阵：此时显示变换已应用，
 * 模型单位直接对应到四边形；这一点早于 Fabric Indigo 可能接管绘制的 {@code isBuiltin()} 钩子。只读取。仅为表现。
 */
@Mixin(ItemRenderer.class)
public abstract class UsecScopeLensItemRenderMixin {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
                    shift = At.Shift.AFTER))
    private void sparkwitch$noteUsecScopeLens(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded,
                                              MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                              int overlay, BakedModel model, CallbackInfo ci) {
        UsecScopeLensAnchors.noteItem(stack, renderMode, matrices.peek().getPositionMatrix());
    }
}
