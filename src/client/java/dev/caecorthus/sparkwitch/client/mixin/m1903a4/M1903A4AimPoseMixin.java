package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.m1903a4.M1903A4FirstPersonRules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the M1903A4 is charged in the main hand, the first-person sprite slides a quarter screen towards the middle, so
 * the weapon reads as centred on the crosshair instead of held off to the right. The hook sits right before the general
 * item branch's {@code renderItem} (ordinal 1, after vanilla's use-action pose), pinned by
 * {@code M1903A4ClientContractTest}.
 * M1903A4 在主手蓄力时把第一人称贴图向屏幕中间平移四分之一屏宽，让武器对着准星而不是偏在右边。注入点位于通用物品分支的
 * {@code renderItem} 之前（序号 1，在原版使用动作姿势之后），由 {@code M1903A4ClientContractTest} 固定。
 */
@Mixin(HeldItemRenderer.class)
public abstract class M1903A4AimPoseMixin {

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void sparkwitch$aimM1903a4(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                       float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // Keyed on the rendered hand and stack: only the main hand that is charging this rifle takes the pose.
        // 按正在渲染的手与物品判断：只有正在为这把步枪蓄力的主手才使用该姿势。
        if (!M1903A4FirstPersonRules.aimPose(item.isOf(SparkWitchItems.m1903a4()), hand == Hand.MAIN_HAND,
                player.isUsingItem(), player.getActiveHand() == hand)) {
            return;
        }
        boolean rightArmed = player.getMainArm() == Arm.RIGHT;
        matrices.translate(rightArmed ? -0.1875F : 0.1875F, -0.08F, 0.1F);
        // Small roll in the screen plane so the scope side leans left instead of right while aiming.
        // 在屏幕平面内轻微滚转，让瞄准镜那一侧往左倾而不是右倾。
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rightArmed ? 12.0F : -12.0F));
    }
}
