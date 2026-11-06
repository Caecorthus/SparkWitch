package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32ClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Two-handed M32A1 pose for every rendered player: the anti-tank launcher's {@code PotionLauncherArmPoseMixin} for the
 * M32A1 (its own {@code m32a1.json} third-person transform puts the pistol grip in the dominant fist with the bore level;
 * the support fist stays about 0.7 block short of the vertical foregrip, which no vanilla pose reaches and arm angles
 * are never written here). The use action is NONE, so vanilla ends at its plain {@code ITEM} answer; only that answer becomes
 * {@code CROSSBOW_HOLD}, any inner pose (Wathe's bat, NoellesRoles' Jester Moment) is kept. A {@code @WrapMethod} at
 * priority 1600 nests outside Wathe's and NoellesRoles' TAIL injects and inside the Blind gate (2000). The shown stack is
 * read at the STORE of vanilla's only {@code ItemStack} local, after Wathe's display substitution, so a viewer shown a
 * fake item never sees the launcher pose. It answers only for the M32A1 and chains with the launcher's wrapper.
 * 所有被渲染玩家的双手持 M32A1 姿势：即反坦克炮筒的 {@code PotionLauncherArmPoseMixin} 用于 M32A1（其自身 {@code m32a1.json}
 * 的第三人称变换让手枪握把落在主手拳中且枪膛水平；辅助手距垂直前握把约 0.7 格，任何原版姿势都够不到，这里也从不写手臂角度）。使用动作为 NONE，原版最终返回普通的 {@code ITEM}；只有该结果改为
 * {@code CROSSBOW_HOLD}，内层给出的姿势（Wathe 球棒、NoellesRoles 小丑时刻）保持不变。优先级 1600 的 {@code @WrapMethod} 嵌套在
 * Wathe 与 NoellesRoles 的 TAIL 注入之外、盲人拦截（2000）之内。显示物品在原版唯一 {@code ItemStack} 局部变量的 STORE 处读取，位于
 * Wathe 显示替换之后，因此看到假物品的观察者永远看不到持炮姿势。它只对 M32A1 作答，并与炮筒的包装串联。
 */
@Mixin(value = PlayerEntityRenderer.class, priority = 1600)
public abstract class M32ArmPoseMixin {
    @ModifyVariable(method = "getArmPose", at = @At("STORE"))
    private static ItemStack sparkwitch$noteShownM32(ItemStack shown, @Share("m32Shown") LocalBooleanRef m32Shown) {
        m32Shown.set(M32ClientState.isLauncher(shown));
        return shown;
    }

    @WrapMethod(method = "getArmPose(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;")
    private static BipedEntityModel.ArmPose sparkwitch$holdM32WithBothHands(
            AbstractClientPlayerEntity player, Hand hand, Operation<BipedEntityModel.ArmPose> original,
            @Share("m32Shown") LocalBooleanRef m32Shown) {
        BipedEntityModel.ArmPose pose = original.call(player, hand);
        return m32Shown.get() && pose == BipedEntityModel.ArmPose.ITEM ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : pose;
    }
}
