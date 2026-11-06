package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
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
 * Third-person straight-arm gun hold for the Savior pistol. Wathe applies this pose to everything in its
 * {@code wathe:guns} tag from {@code BipedEntityModelMixin}; the pistol is deliberately not in that tag (the tag also
 * carries Wathe's gun pickup/drop rules), so the pose is copied here, scoped to {@code sparkwitch:savior_pistol}.
 * <p>
 * Same two TAIL injects and the same math as Wathe: the hand holding the pistol is straightened forward along the
 * head direction, which is what the revolver-style item display in {@code savior_pistol.json} is fitted to. The shown
 * stack follows Wathe's psychosis substitution (the viewer's hallucination item replaces the real main hand), so a
 * viewer seeing a fake item never sees a gun pose. Runs inside the Blind gate's {@code positionRightArm} /
 * {@code positionLeftArm} wraps, so blind viewers keep {@code EMPTY}.
 * 救世主手枪的第三人称平举持枪姿势。Wathe 在 {@code BipedEntityModelMixin} 里对 {@code wathe:guns} 标签内的物品施加这个
 * 姿势；手枪刻意没有进那个标签（标签还带 Wathe 的掉枪/拾取规则），所以这里复制一份，只对
 * {@code sparkwitch:savior_pistol} 生效。
 * <p>
 * 与 Wathe 相同的两处 TAIL 注入和同样的计算：持枪那只手沿头部朝向平举向前，{@code savior_pistol.json} 里左轮式的物品
 * display 正是按这个姿势对齐的。显示物品遵循 Wathe 的精神错乱替换（观察者的幻觉物品会替换真实主手），所以看到假物品的
 * 观察者不会看到持枪姿势。本注入位于盲人视图的 {@code positionRightArm} / {@code positionLeftArm} 包裹之内，盲人
 * 观察者保持 {@code EMPTY}。
 */
@Mixin(BipedEntityModel.class)
public abstract class SaviorPistolArmPoseMixin<T extends LivingEntity> {
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
    private void sparkwitch$holdSaviorPistolRight(T entity, CallbackInfo ci) {
        if (entity.getMainArm() == Arm.RIGHT && sparkwitch$isHoldingSaviorPistol(entity)) {
            sparkwitch$holdGun(this.rightArm, this.head, true);
        }
    }

    @Inject(method = "positionLeftArm(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sparkwitch$holdSaviorPistolLeft(T entity, CallbackInfo ci) {
        if (entity.getMainArm() != Arm.RIGHT && sparkwitch$isHoldingSaviorPistol(entity)) {
            sparkwitch$holdGun(this.leftArm, this.head, false);
        }
    }

    /**
     * Main hand as the viewer actually sees it: Wathe's CCA hallucination item wins when present, otherwise the real
     * stack. Mirrors Wathe's {@code isHoldingGun}.
     * 观察者实际看到的主手：有 Wathe 的幻觉物品时优先用它，否则用真实物品堆。与 Wathe 的 {@code isHoldingGun} 一致。
     */
    @Unique
    private boolean sparkwitch$isHoldingSaviorPistol(T entity) {
        ItemStack shown = entity.getMainHandStack();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(client.player);
            ItemStack psychosis = mood.getPsychosisItems().get(entity.getUuid());
            if (psychosis != null) {
                shown = psychosis;
            }
        }
        return shown.isOf(SparkWitchItems.saviorPistol());
    }

    /** Wathe's straight-arm hold math, verbatim. / 与 Wathe 平举持枪完全相同的计算。 */
    @Unique
    private static void sparkwitch$holdGun(ModelPart arm, ModelPart head, boolean rightArmed) {
        arm.yaw = (rightArmed ? -0.3F : 0.3F) + head.yaw;
        arm.pitch = (-(float) Math.PI / 2F) + head.pitch + 0.1F;
    }
}
