package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorLightsaberDrawAnimation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person arm animation for the Savior lightsaber. The saber arm follows the draw/animation angles and the other
 * arm is the support arm. Which arm is which follows the player's main arm, so left-handed players get the mirrored
 * animation: the saber arm is the left arm and the support arm is the right one. Left-arm poses mirror the authored
 * right-arm values (pitch unchanged, yaw and roll negated), matching vanilla's own left/right arm math.
 * 救世主光剑的第三人称手臂动画。持剑手播放拔出/攻击动画，另一只手作为支撑手。哪只手是哪只跟随玩家的主手，
 * 因此主手左手的玩家得到镜像动画：左手持剑、右手支撑。左臂姿势对右手参考值做镜像（pitch 不变，yaw/roll 取反），
 * 与原版自身的左右臂换算一致。
 */
@Mixin(BipedEntityModel.class)
public abstract class SaviorLightsaberBipedPoseMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "positionRightArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$poseRightArm(T entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !sparkwitch$isPosed(player)) {
            return;
        }
        if (player.getMainArm() == Arm.RIGHT) {
            sparkwitch$poseSaberArm(this.rightArm, player, false);
        } else {
            sparkwitch$poseSupportArm(this.rightArm, player, true);
        }
    }

    @Inject(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$poseLeftArm(T entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !sparkwitch$isPosed(player)) {
            return;
        }
        if (player.getMainArm() == Arm.LEFT) {
            sparkwitch$poseSaberArm(this.leftArm, player, true);
        } else {
            sparkwitch$poseSupportArm(this.leftArm, player, false);
        }
    }

    /**
     * Runs for every biped model twice a frame: the held-item check comes before the animation-state map lookup.
     * 每帧对每个双足模型执行两次：先判断手持物品，再查动画状态表。
     */
    @Unique
    private static boolean sparkwitch$isPosed(PlayerEntity player) {
        return player.getMainHandStack().isOf(SparkWitchItems.saviorLightsaber())
                && SaviorLightsaberDrawAnimation.isHolding(player);
    }

    /** Saber arm: the whole arm is replaced by the draw animation's current angles. / 持剑手：整条手臂替换为拔出动画当前的角度。 */
    @Unique
    private void sparkwitch$poseSaberArm(ModelPart arm, PlayerEntity player, boolean mirrored) {
        SaviorLightsaberDrawAnimation.ArmAngles angles =
                SaviorLightsaberDrawAnimation.armAngles(player, this.head.pitch);
        arm.pitch = angles.pitch();
        arm.yaw = mirrored ? -angles.yaw() : angles.yaw();
        arm.roll = mirrored ? -angles.roll() : angles.roll();
    }

    /**
     * Support arm by state, then the attack overrides it: swing goes back 25° → forward 20°, hold keeps forward 20°.
     * 先按状态算支撑手，再用攻击覆盖：挥砍时从后 25° 转到前 20°，保持阶段停在前 20°。
     */
    @Unique
    private void sparkwitch$poseSupportArm(ModelPart arm, PlayerEntity player, boolean mirrored) {
        float pitch = arm.pitch;
        float yaw = arm.yaw;
        float roll = arm.roll;
        float run = SaviorLightsaberDrawAnimation.runningBlend(player);
        if (run <= 0.0F && SaviorLightsaberDrawAnimation.blockHoldActive(player)) {
            pitch = SaviorLightsaberDrawAnimation.LEFT_ARM_BACK_PITCH;
            yaw = 0.0F;
            roll = 0.0F;
        } else if (run > 0.0F) {
            // Point the arm along the head's look direction: pitch = 90° - head pitch, yaw = head yaw + 180°.
            // The right support arm uses the mirrored yaw.
            // 手臂朝视角方向：pitch = 90° - 头部俯仰，yaw = 头部偏航 + 180°。右侧支撑手使用镜像后的 yaw。
            float targetPitch = (float) (Math.PI / 2.0) - this.head.pitch;
            float targetYaw = this.head.yaw + (float) Math.PI;
            if (mirrored) {
                targetYaw = -targetYaw;
            }
            float delta = targetYaw - yaw;
            if (delta > Math.PI) {
                targetYaw -= (float) (Math.PI * 2.0);
            } else if (delta < -Math.PI) {
                targetYaw += (float) (Math.PI * 2.0);
            }
            pitch += (targetPitch - pitch) * run;
            yaw += (targetYaw - yaw) * run;
            roll += (0.0F - roll) * run;
        } else if (SaviorLightsaberDrawAnimation.shouldRaiseLeftArm(player)) {
            pitch = SaviorLightsaberDrawAnimation.leftArmPitch(player);
            yaw = 0.0F;
            roll = 0.0F;
        }
        float weight = SaviorLightsaberDrawAnimation.attackWeight(player);
        if (weight > 0.0F) {
            float k = SaviorLightsaberDrawAnimation.attackSwingBlend(player);
            float attackPitch = SaviorLightsaberDrawAnimation.LEFT_ARM_ATTACK_BACK_PITCH
                    + (SaviorLightsaberDrawAnimation.LEFT_ARM_ATTACK_FRONT_PITCH
                    - SaviorLightsaberDrawAnimation.LEFT_ARM_ATTACK_BACK_PITCH) * k;
            pitch += (attackPitch - pitch) * weight;
            yaw += (0.0F - yaw) * weight;
            roll += (0.0F - roll) * weight;
        }
        arm.pitch = pitch;
        arm.yaw = yaw;
        arm.roll = roll;
    }
}
