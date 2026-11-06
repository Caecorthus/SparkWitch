package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lowers the support (left) hand on the Savior lightsaber. Vanilla's crossbow-charge pose sets both arms to the same
 * pitch, so the supporting hand ends up level with the item hand and visually grabs the blade; the owner asked for the
 * left hand about half a hand lower. Pitch is increased by 0.25 rad after the pose is applied, which drops the hand
 * roughly 2 model pixels (half of the 4-pixel hand). Vertical hand position depends only on the arm pitch (yaw and
 * roll do not change it), so this is a pure vertical drop.
 * <p>
 * Both position methods are hooked: whichever one applies the charge pose last, its TAIL drops the left hand exactly
 * once (an earlier drop is overwritten by the charge pose).
 * 降低救世主光剑的支撑手（左手）。原版弩蓄力姿势把双臂设成相同俯仰，支撑手与持剑手同高、看起来抓住了刀刃；所有者要求
 * 左手大约低半个手。姿势应用后把俯仰增加 0.25 弧度，手部约下降 2 个模型像素（4 像素手的一半）。手的垂直位置只取决于
 * 手臂俯仰（偏航与翻滚不影响高度），因此这是纯垂直下降。
 * <p>
 * 两个 position 方法都挂接：无论哪一个最后应用蓄力姿势，它的 TAIL 都只会让左手下降一次（更早的下降会被蓄力姿势覆盖）。
 */
@Mixin(BipedEntityModel.class)
public abstract class SaviorLightsaberBipedPoseMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "positionRightArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$lowerSupportHandRight(T entity, CallbackInfo ci) {
        sparkwitch$lowerSupportHand(entity);
    }

    @Inject(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$lowerSupportHandLeft(T entity, CallbackInfo ci) {
        sparkwitch$lowerSupportHand(entity);
    }

    @Unique
    private void sparkwitch$lowerSupportHand(T entity) {
        if (entity.getMainHandStack().isOf(SparkWitchItems.saviorLightsaber())) {
            // ~14 degrees down (half a hand), then a small yaw nudge toward the item hand (screen right).
            // 约下移 14 度（半只手），再小幅偏航朝持剑手方向（屏幕右侧）挪一点。
            this.leftArm.pitch += 0.25F;
            this.leftArm.yaw += 0.2F;
        }
    }
}
