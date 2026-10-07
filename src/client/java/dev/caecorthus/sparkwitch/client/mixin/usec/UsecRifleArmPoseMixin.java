package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.usec.UsecRifleClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two-handed rifle pose for every rendered player (plan D15, launcher precedent {@code PotionLauncherArmPoseMixin}):
 * the hand whose shown stack is the USEC rifle answers {@code CROSSBOW_HOLD}, which the biped model applies to both
 * arms. The shown stack is vanilla's own {@code itemStack} local, i.e. what {@code getStackInHand} returned after
 * Wathe's renderer substitution, so a viewer who sees a fake item never sees the rifle pose. It modifies the return
 * value, so it chains with Wathe's TAIL hook and the launcher's wrapper on the same method; the Blind gate wraps the
 * whole method and still wins for Blind viewers.
 * 所有被渲染玩家的双手持枪姿势（计划 D15，参照炮筒 {@code PotionLauncherArmPoseMixin}）：显示的物品为 USEC 步枪的那只手
 * 返回 {@code CROSSBOW_HOLD}，由双足模型应用到双臂。显示的物品取原版自己的 {@code itemStack} 局部变量，即 Wathe 渲染替换后
 * {@code getStackInHand} 的返回值，因此看到假物品的观察者永远看不到持枪姿势。它修改返回值，因此可与 Wathe 的 TAIL 钩子及
 * 炮筒在同一方法上的包装串联；盲人闸门包装整个方法，对盲人观察者仍然优先。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class UsecRifleArmPoseMixin {
    @ModifyReturnValue(method = "getArmPose", at = @At("RETURN"))
    private static BipedEntityModel.ArmPose sparkwitch$holdUsecRifleWithBothHands(
            BipedEntityModel.ArmPose original, AbstractClientPlayerEntity player, Hand hand,
            @Local ItemStack shown) {
        return UsecRifleClient.isRifle(shown) ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : original;
    }
}
