package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the M1903A4 is charged, the first-person sprite slides a quarter screen towards the middle, so the weapon
 * reads as centred on the crosshair instead of held off to the right.
 * M1903A4 蓄力时把第一人称贴图向屏幕中间平移四分之一屏宽，让武器对着准星而不是偏在右边。
 */
@Mixin(net.minecraft.client.render.item.HeldItemRenderer.class)
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
        if (!sparkwitch$aiming(player, item)) {
            return;
        }
        boolean rightArmed = player.getMainArm() == Arm.RIGHT;
        matrices.translate(rightArmed ? -0.1875F : 0.1875F, -0.08F, 0.1F);
        // Small roll in the screen plane so the scope side leans left instead of right while aiming.
        // 在屏幕平面内轻微滚转，让瞄准镜那一侧往左倾而不是右倾。
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rightArmed ? 12.0F : -12.0F));
    }

    @Unique
    private static boolean sparkwitch$aiming(AbstractClientPlayerEntity player, ItemStack item) {
        return item.isOf(SparkWitchItems.m1903a4())
                && player.isUsingItem()
                && player.getActiveHand() == Hand.MAIN_HAND;
    }
}
