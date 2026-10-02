package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Arm-pose gate, part two: Wathe's TAIL on {@code positionRightArm} / {@code positionLeftArm} raises a gun-holding arm
 * straight forward whatever the arm pose is. While the Blind view is active these outermost whole-method wraps run
 * only vanilla's {@code EMPTY} branch (the arm's yaw reset; the walk swing set earlier in {@code setAngles} stays) for
 * another player, so neither Wathe's gun hold nor any other pose positioning reaches the line art. Every other entity,
 * and every player outside the view, keeps the original. Presentation only.
 * 手臂姿势闸门之二：Wathe 在 {@code positionRightArm} / {@code positionLeftArm} 的 TAIL 上会无视手臂姿势、把持枪手臂
 * 平举向前。盲人视图生效期间，这两个最外层整方法包裹对其他玩家只执行原版 {@code EMPTY} 分支（重置手臂偏航；
 * {@code setAngles} 之前设置的行走摆臂保留），因此 Wathe 的持枪姿势与其他任何姿势定位都不会进入线稿。其他实体以及
 * 视图之外的玩家保持原样。仅为展示。
 */
@Mixin(value = BipedEntityModel.class, priority = 2000)
public abstract class BlindGateBipedEntityModelMixin {
    @WrapMethod(method = "positionRightArm(Lnet/minecraft/entity/LivingEntity;)V")
    private void sparkwitch$blindEmptyRightArm(LivingEntity entity, Operation<Void> original) {
        if (BlindClientGates.suppressesFeatures(entity)) {
            ((BipedEntityModel<?>) (Object) this).rightArm.yaw = 0.0F;
            return;
        }
        original.call(entity);
    }

    @WrapMethod(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V")
    private void sparkwitch$blindEmptyLeftArm(LivingEntity entity, Operation<Void> original) {
        if (BlindClientGates.suppressesFeatures(entity)) {
            ((BipedEntityModel<?>) (Object) this).leftArm.yaw = 0.0F;
            return;
        }
        original.call(entity);
    }
}
