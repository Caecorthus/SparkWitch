package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Arm-pose gate, part one: while the Blind view is active, another player's arm pose is always {@code EMPTY}, so line
 * art and silhouettes never show a weapon stance (Wathe's bat crossbow-charge pose, the knife's spear throw while it is
 * used, NoellesRoles' Jester Moment pose, bows and crossbows). The outermost whole-method wrap encloses Wathe's and
 * NoellesRoles' TAIL overrides and Wathe's held-item swap inside {@code getArmPose}. Part two,
 * {@code BlindGateBipedEntityModelMixin}, removes Wathe's straight-arm gun hold, which ignores the arm pose.
 * Presentation only.
 * 手臂姿势闸门之一：盲人视图生效期间，其他玩家的手臂姿势恒为 {@code EMPTY}，线稿与轮廓因此不会露出持械姿势（Wathe
 * 球棒的弩蓄力姿势、使用刀时的投矛姿势、NoellesRoles 小丑时刻姿势、弓与弩）。最外层的整方法包裹覆盖 Wathe 与
 * NoellesRoles 在 {@code getArmPose} 上的 TAIL 覆盖以及 Wathe 的手持物替换。之二 {@code BlindGateBipedEntityModelMixin}
 * 去掉 Wathe 不看手臂姿势的直臂持枪。仅为展示。
 */
@Mixin(value = PlayerEntityRenderer.class, priority = 2000)
public abstract class BlindGatePlayerEntityRendererMixin {
    @WrapMethod(method = "getArmPose(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;")
    private static BipedEntityModel.ArmPose sparkwitch$blindEmptyArmPose(AbstractClientPlayerEntity player, Hand hand,
                                                                       Operation<BipedEntityModel.ArmPose> original) {
        if (BlindClientGates.suppressesFeatures(player)) {
            return BipedEntityModel.ArmPose.EMPTY;
        }
        return original.call(player, hand);
    }
}
