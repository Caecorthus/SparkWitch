package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Third-person hold pose for the Savior lightsaber: any hand holding it answers {@code CROSSBOW_CHARGE}, exactly like
 * Wathe's bat ({@code wathe$customArmPose}). The two-handed crossbow charge pose raises both arms, so the saber is held
 * in front of the body instead of hanging at the side; the item model's third-person display transform is fitted to
 * this pose in {@code savior_lightsaber_handheld.json}.
 * <p>
 * The hook mirrors Wathe: a cancellable TAIL inject, so watch order and inner overrides are unchanged. It runs inside
 * the SparkWitch Blind gate's outermost {@code getArmPose} wrap, so blind viewers still get {@code EMPTY}.
 * 救世主光剑的第三人称持握姿势：任一手持剑时返回 {@code CROSSBOW_CHARGE}，与 Wathe 球棒
 * （{@code wathe$customArmPose}）完全一致。弩蓄力姿势会抬起双臂，使光剑端在身前而不是垂在手边；物品模型的第三人称
 * display 变换在 {@code savior_lightsaber_handheld.json} 中按这个姿势对齐。
 * <p>
 * 钩子照抄 Wathe：可取消的 TAIL 注入，不改变与其他覆盖的顺序。它位于 SparkWitch 盲人视图最外层 {@code getArmPose}
 * 包裹之内，因此盲人观察者仍然得到 {@code EMPTY}。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class SaviorLightsaberArmPoseMixin {
    @Inject(
            method = "getArmPose(Lnet/minecraft/client/network/AbstractClientPlayerEntity;"
                    + "Lnet/minecraft/util/Hand;)"
                    + "Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;",
            at = @At("TAIL"),
            cancellable = true
    )
    private static void sparkwitch$holdSaviorLightsaber(AbstractClientPlayerEntity player, Hand hand,
                                                       CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
        if (player.getStackInHand(hand).isOf(SparkWitchItems.saviorLightsaber())) {
            cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_CHARGE);
        }
    }
}
