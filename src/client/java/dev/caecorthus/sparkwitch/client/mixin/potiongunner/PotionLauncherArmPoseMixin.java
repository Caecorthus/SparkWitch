package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two-handed launcher pose for every rendered player: the hand holding the launcher answers
 * {@code CROSSBOW_HOLD}, which the biped model applies to both arms. It modifies the return value, so it chains with
 * Wathe's TAIL hook on the same method (bat pose) and its held-stack override.
 * 所有被渲染玩家的双手持炮姿势：持有炮筒的手返回 {@code CROSSBOW_HOLD}，由双足模型应用到双臂。它修改返回值，因此可与
 * Wathe 在同一方法上的 TAIL 钩子（球棒姿势）及其手持物替换串联。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PotionLauncherArmPoseMixin {
    @ModifyReturnValue(method = "getArmPose", at = @At("RETURN"))
    private static BipedEntityModel.ArmPose sparkwitch$holdLauncherWithBothHands(
            BipedEntityModel.ArmPose original, AbstractClientPlayerEntity player, Hand hand) {
        return PotionScopeClient.isLauncher(player.getStackInHand(hand))
                ? BipedEntityModel.ArmPose.CROSSBOW_HOLD
                : original;
    }
}
