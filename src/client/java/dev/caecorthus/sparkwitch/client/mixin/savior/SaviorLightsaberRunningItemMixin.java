package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorLightsaberDrawAnimation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the sword/arm wrist angle: 120 degrees while standing or walking, 165 degrees while sprinting, by rotating the
 * held saber about its grip. The axis is fixed in the hand frame (the arm's local Y axis crossed with the saber's blade
 * axis); the rotation is applied before the item display transform, so the grip stays in the palm.
 * 站立/走路时剑与胳膊夹角 120 度，跑步时 165 度：把光剑绕握点旋转。旋转轴在手的局部坐标里由手臂轴与剑身轴叉乘
 * 得到，在物品显示变换之前应用，因此握点仍贴在手里。
 */
@Mixin(ItemRenderer.class)
public class SaviorLightsaberRunningItemMixin {
    /** Standing/walking: sword stays close to the arm; running / post-swing: sword bends further out.
     *  站立/走路：剑身更贴手臂；跑步/挥砍收招：剑身折出去更多。 */
    private static final float BASE_TILT_RAD = 3.6F * ((float) Math.PI / 180.0F);
    private static final float RUNNING_EXTRA_RAD = 45.0F * ((float) Math.PI / 180.0F);
    /** The wrist angle changes in the hand-local YZ plane, so the axis is the hand-local X axis.
     *  腕角在手部局部 YZ 平面内变化，旋转轴为手部局部 X 轴。 */
    private static final float AXIS_X = -1.0F;
    private static final float AXIS_Z = 0.0F;
    /** Hilt middle in the hand frame (blocks) under the vanilla handheld transform. / 原版手持变换下剑柄中间在手坐标系的坐标（格）。 */
    private static final float PIVOT_Y = -0.1222F;
    private static final float PIVOT_Z = 0.1111F;

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("HEAD")
    )
    private void sparkwitch$tiltRunningSaber(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                             boolean leftHand, MatrixStack matrices,
                                             VertexConsumerProvider vertexConsumers, World world,
                                             int light, int overlay, int seed, CallbackInfo ci) {
        if (mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                || !stack.isOf(SparkWitchItems.saviorLightsaber())
                || !(entity instanceof PlayerEntity player)) {
            return;
        }
        float amount = SaviorLightsaberDrawAnimation.runningBlend(player);
        float attackWeight = SaviorLightsaberDrawAnimation.attackWeight(player);
        if (attackWeight > 0.0F) {
            float k = SaviorLightsaberDrawAnimation.attackSwingBlend(player);
            amount += (k - amount) * attackWeight;
        }
        float tilt = BASE_TILT_RAD + RUNNING_EXTRA_RAD * amount;
        // Rotate around the middle of the hilt instead of the hand origin. / 绕剑柄中间而不是手原点旋转。
        matrices.translate(0.0F, PIVOT_Y, PIVOT_Z);
        matrices.multiply(new Quaternionf().rotationAxis(tilt, AXIS_X, 0.0F, AXIS_Z));
        matrices.translate(0.0F, -PIVOT_Y, -PIVOT_Z);
    }
}
