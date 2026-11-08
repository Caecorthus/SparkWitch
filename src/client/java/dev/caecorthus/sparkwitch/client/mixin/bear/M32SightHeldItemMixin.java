package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32SightRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The M32A1's 3-D M2A1 aim in the local first-person hand pass ({@link M32SightRenderer}). HEAD and TAIL of the
 * first-person {@code renderItem(float, MatrixStack, Immediate, ClientPlayerEntity, int)} bracket the pass and capture
 * its base matrix (vanilla's {@code renderHand} and Iris's {@code HandRenderer} both call it; the base already carries
 * hurt tilt and view bob). The {@code @WrapOperation} sits on the single {@code ItemRenderer#renderItem(LivingEntity,
 * ...)} call inside {@code renderItem(LivingEntity, ...)}, so the wrapping method's other hooks keep running (Wathe's
 * AFTER hand-particle/muzzle inject on the same call, SparkTraits' Last Stand HEAD cancel, SparkStrength's skateboard
 * HEAD cancel of {@code renderFirstPersonItem}). It answers only for this renderer's own main-hand stack (the exact
 * {@code mainHand} reference vanilla passes down); everything else, and the M32A1 at aim progress 0, makes the original
 * call with the original arguments. Client-only presentation.
 * M32A1 在本地第一人称手部渲染中的三维 M2A1 瞄准（{@link M32SightRenderer}）。第一人称
 * {@code renderItem(float, MatrixStack, Immediate, ClientPlayerEntity, int)} 的 HEAD 与 TAIL 界定本次渲染并捕获其基准矩阵（原版
 * {@code renderHand} 与 Iris 的 {@code HandRenderer} 都调用它；基准已包含受伤倾斜与视角摇晃）。{@code @WrapOperation} 位于
 * {@code renderItem(LivingEntity, ...)} 中唯一的 {@code ItemRenderer#renderItem(LivingEntity, ...)} 调用上，因此所在方法的其他钩子
 * 照常运行（Wathe 在同一调用 AFTER 处的手部粒子/枪口注入、SparkTraits 背水一战的 HEAD 取消、SparkStrength 滑板对
 * {@code renderFirstPersonItem} 的 HEAD 取消）。它只对本渲染器自己的主手物品（原版向下传递的 {@code mainHand} 引用本身）作答；
 * 其他一切，以及瞄准进度为 0 的 M32A1，都以原参数执行原调用。纯客户端展示。
 */
@Mixin(HeldItemRenderer.class)
public abstract class M32SightHeldItemMixin {
    @Shadow
    private ItemStack mainHand;

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"
            + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void sparkwitch$beginM32SightPass(float tickDelta, MatrixStack matrices,
                                              VertexConsumerProvider.Immediate vertexConsumers,
                                              ClientPlayerEntity player, int light, CallbackInfo ci) {
        M32SightRenderer.beginHandPass(matrices, tickDelta);
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"
            + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
    private void sparkwitch$endM32SightPass(float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider.Immediate vertexConsumers,
                                            ClientPlayerEntity player, int light, CallbackInfo ci) {
        M32SightRenderer.endHandPass();
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem("
                    + "Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;Z"
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/world/World;III)V"))
    private void sparkwitch$drawAimedM32(ItemRenderer renderer, LivingEntity entity, ItemStack stack,
                                         ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                         VertexConsumerProvider consumers, World world, int light, int overlay, int seed,
                                         Operation<Void> original) {
        if (stack != this.mainHand || !M32SightRenderer.draw(renderer, entity, stack, mode, leftHanded, matrices,
                consumers, world, light, overlay, seed)) {
            original.call(renderer, entity, stack, mode, leftHanded, matrices, consumers, world, light, overlay, seed);
        }
    }
}
