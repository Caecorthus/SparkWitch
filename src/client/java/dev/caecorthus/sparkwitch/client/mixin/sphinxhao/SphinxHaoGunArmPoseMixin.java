package dev.caecorthus.sparkwitch.client.mixin.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunInventory;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person straight-arm gun hold for the Sphinx Hao 「“手”枪」, copied from {@code SaviorPistolArmPoseMixin}: Wathe
 * applies this pose only to its {@code wathe:guns} tag, and the gun stays out of that tag (it also carries Wathe's gun
 * pickup, drop and punishment rules). Same two TAIL injects and Wathe's math; the gun's third-person display is fitted
 * to it. The shown main hand follows Wathe's psychosis substitution, so a viewer seeing a hallucination never sees the
 * pose. Runs inside the Blind gate's arm wraps, so blind viewers keep {@code EMPTY}.
 * 狮身豪「“手”枪」的第三人称平举持枪姿势，复制自 {@code SaviorPistolArmPoseMixin}：Wathe 只对 {@code wathe:guns} 标签施加该姿势，
 * 而这把枪不在该标签内（标签还带有 Wathe 的拾枪、掉枪与误杀惩罚规则）。同样两处 TAIL 注入与 Wathe 的计算；手枪的第三人称 display
 * 按此姿势对齐。显示的主手遵循 Wathe 的精神错乱替换，看到幻觉的观察者不会看到该姿势。位于盲人视图的手臂包裹之内，盲人观察者保持
 * {@code EMPTY}。
 */
@Mixin(BipedEntityModel.class)
public abstract class SphinxHaoGunArmPoseMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "positionRightArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$holdSphinxHaoGunRight(T entity, CallbackInfo ci) {
        if (sparkwitch$isHoldingSphinxHaoGun(entity) && entity.getMainArm() == Arm.RIGHT) {
            sparkwitch$holdSphinxHaoGun(this.rightArm, this.head, true);
        }
    }

    @Inject(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$holdSphinxHaoGunLeft(T entity, CallbackInfo ci) {
        if (sparkwitch$isHoldingSphinxHaoGun(entity) && entity.getMainArm() != Arm.RIGHT) {
            sparkwitch$holdSphinxHaoGun(this.leftArm, this.head, false);
        }
    }

    /**
     * Main hand as the viewer sees it; cheap class check first, the viewer's mood component only for a real holder.
     * A hallucination never holds this gun, so it can only hide it. / 观察者看到的主手；先做廉价的类判断，只有真正持枪时才读取
     * 观察者的情绪组件。幻觉物品从不是这把枪，因此只会遮住它。
     */
    @Unique
    private boolean sparkwitch$isHoldingSphinxHaoGun(T entity) {
        if (!SphinxHaoGunInventory.isGun(entity.getMainHandStack())) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(entity instanceof PlayerEntity) || client.player == null) {
            return true;
        }
        ItemStack psychosis = PlayerMoodComponent.KEY.get(client.player).getPsychosisItems().get(entity.getUuid());
        return psychosis == null || SphinxHaoGunInventory.isGun(psychosis);
    }

    /** Wathe's straight-arm hold math, verbatim. / 与 Wathe 平举持枪完全相同的计算。 */
    @Unique
    private static void sparkwitch$holdSphinxHaoGun(ModelPart arm, ModelPart head, boolean rightArmed) {
        arm.yaw = (rightArmed ? -0.3F : 0.3F) + head.yaw;
        arm.pitch = (-(float) Math.PI / 2F) + head.pitch + 0.1F;
    }
}
