package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two-handed launcher pose for every rendered player: the hand whose shown stack is the launcher answers
 * {@code CROSSBOW_HOLD}, which the biped model applies to both arms. The shown stack is vanilla's own
 * {@code itemStack} local, the value {@code getStackInHand} returned after Wathe's {@code PlayerEntityRendererMixin}
 * substituted it (main hand of a visible player: a note shows empty, and a psychotic viewer sees the psychosis item).
 * It is never re-read from the inventory, so a viewer who sees a fake item never sees the launcher pose, and a fake
 * launcher gets the pose its picture needs. It modifies the return value, so it chains with Wathe's TAIL hook on the
 * same method (bat pose).
 * 所有被渲染玩家的双手持炮姿势：显示的物品为炮筒的那只手返回 {@code CROSSBOW_HOLD}，由双足模型应用到双臂。显示的物品
 * 取原版自己的 {@code itemStack} 局部变量，即 Wathe 的 {@code PlayerEntityRendererMixin} 替换后
 * {@code getStackInHand} 的返回值（可见玩家的主手：便签显示为空，精神错乱的观察者看到幻觉物品）。它从不重新读取物品栏，
 * 因此看到假物品的观察者永远看不到持炮姿势，而幻觉中的炮筒也会得到与画面一致的姿势。它修改返回值，因此可与 Wathe 在
 * 同一方法上的 TAIL 钩子（球棒姿势）串联。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PotionLauncherArmPoseMixin {
    @ModifyReturnValue(method = "getArmPose", at = @At("RETURN"))
    private static BipedEntityModel.ArmPose sparkwitch$holdLauncherWithBothHands(
            BipedEntityModel.ArmPose original, AbstractClientPlayerEntity player, Hand hand,
            @Local ItemStack shown) {
        return PotionScopeClient.isLauncher(shown) ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : original;
    }
}
