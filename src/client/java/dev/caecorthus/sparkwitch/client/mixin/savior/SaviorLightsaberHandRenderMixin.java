package dev.caecorthus.sparkwitch.client.mixin.savior;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorHandRenderScope;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a Savior lightsaber drawn in an entity's hand ({@link SaviorHandRenderScope}) for the duration of the
 * entity-taking {@code ItemRenderer.renderItem}, which resolves the model (and so the lightsaber's model predicates)
 * before drawing it. Every first- and third-person hand item goes through this overload; GUI slots do not, so the draw
 * animation's hidden model and the lowering hilt stay off hotbar, inventory and shop icons. Other items pass untouched.
 * 在接收实体的 {@code ItemRenderer.renderItem} 执行期间标记实体手中绘制的救世主光剑（{@link SaviorHandRenderScope}）；该方法
 * 先解析模型（因而先求值光剑的模型谓词）再绘制。第一人称与第三人称手持物品都走这个重载，GUI 格子不走，因此拔出动画的隐藏
 * 模型与放下时的剑柄不会出现在快捷栏、物品栏与商店图标上。其他物品不受影响。
 */
@Mixin(ItemRenderer.class)
public abstract class SaviorLightsaberHandRenderMixin {
    private static final String RENDER_ITEM = "renderItem(Lnet/minecraft/entity/LivingEntity;"
            + "Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
            + "ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;"
            + "Lnet/minecraft/world/World;III)V";

    @Inject(method = RENDER_ITEM, at = @At("HEAD"))
    private void sparkwitch$enterLightsaberHandRender(LivingEntity entity, ItemStack stack,
                                                      ModelTransformationMode mode, boolean leftHanded,
                                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                      World world, int light, int overlay, int seed, CallbackInfo ci,
                                                      @Share("saviorEntered") LocalBooleanRef entered,
                                                      @Share("saviorPrevious") LocalRef<ItemStack> previous) {
        if (stack.isOf(SparkWitchItems.saviorLightsaber())) {
            previous.set(SaviorHandRenderScope.enter(stack));
            entered.set(true);
        }
    }

    @Inject(method = RENDER_ITEM, at = @At("RETURN"))
    private void sparkwitch$exitLightsaberHandRender(LivingEntity entity, ItemStack stack,
                                                     ModelTransformationMode mode, boolean leftHanded,
                                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                     World world, int light, int overlay, int seed, CallbackInfo ci,
                                                     @Share("saviorEntered") LocalBooleanRef entered,
                                                     @Share("saviorPrevious") LocalRef<ItemStack> previous) {
        if (entered.get()) {
            SaviorHandRenderScope.exit(previous.get());
        }
    }
}
