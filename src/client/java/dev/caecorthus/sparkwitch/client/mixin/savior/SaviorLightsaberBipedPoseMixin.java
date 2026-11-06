package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.client.savior.SaviorLightsaberDrawAnimation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person right-arm animation for the Savior lightsaber. Vanilla applies the arm pose inside
 * {@code positionRightArm}; the TAIL override then replaces the whole right arm with the draw animation's current
 * angles: 0.2 s reach to the front-left waist, 0.3 s settle to the down-right-back hold, then stay. The left arm is
 * deliberately untouched here, so the vanilla support-arm animation plays.
 * 救世主光剑的第三人称右臂动画。原版在 {@code positionRightArm} 内应用臂姿；TAIL 覆盖随后把整条右臂替换为拔出动画
 * 当前的三个角度：0.2 秒伸向左前腰、0.3 秒落到下方偏右偏后的持握姿势并保持。这里刻意不碰左臂，因此支撑手播放原版动画。
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
    private void sparkwitch$animateDraw(T entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !SaviorLightsaberDrawAnimation.isHolding(player)) {
            return;
        }
        SaviorLightsaberDrawAnimation.ArmAngles angles =
                SaviorLightsaberDrawAnimation.armAngles(player, this.head.pitch);
        this.rightArm.pitch = angles.pitch();
        this.rightArm.yaw = angles.yaw();
        this.rightArm.roll = angles.roll();
    }

    /** Normal left arm by state, then the attack overrides it: swing goes back 25° → forward 20°, hold keeps forward 20°.
     *  先按状态算常态左臂，再用攻击覆盖：挥砍时从后 25° 转到前 20°，保持阶段停在前 20°。 */
    @Inject(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$overrideLeftArm(T entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !SaviorLightsaberDrawAnimation.isHolding(player)) {
            return;
        }
        float pitch = this.leftArm.pitch;
        float yaw = this.leftArm.yaw;
        float roll = this.leftArm.roll;
        float run = SaviorLightsaberDrawAnimation.runningBlend(player);
        if (run <= 0.0F && SaviorLightsaberDrawAnimation.blockHoldActive(player)) {
            pitch = SaviorLightsaberDrawAnimation.LEFT_ARM_BACK_PITCH;
            yaw = 0.0F;
            roll = 0.0F;
        } else if (run > 0.0F) {
            // Point the arm along the head's look direction: pitch = 90° - head pitch, yaw = head yaw + 180°.
            float targetPitch = (float) (Math.PI / 2.0) - this.head.pitch;
            float targetYaw = this.head.yaw + (float) Math.PI;
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
        this.leftArm.pitch = pitch;
        this.leftArm.yaw = yaw;
        this.leftArm.roll = roll;
    }
}
