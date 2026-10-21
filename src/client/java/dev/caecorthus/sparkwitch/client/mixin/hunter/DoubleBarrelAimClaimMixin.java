package dev.caecorthus.sparkwitch.client.mixin.hunter;

import dev.caecorthus.sparkwitch.client.hunter.HunterShotgunAimClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the shotgun aim claim at the one place every item use packet starts, before vanilla's slot sync and the use
 * packet itself, with the yaw and pitch that packet will carry. Never cancels: a refused use leaves a claim the server
 * drops after one tick.
 * 在每个物品使用数据包的唯一起点发送猎枪瞄准声明：早于原版的栏位同步与使用数据包本身，且朝向与该数据包一致。
 * 从不取消：被拒绝的使用留下的声明会在一刻后被服务端丢弃。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class DoubleBarrelAimClaimMixin {
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void sparkwitch$claimShotgunAim(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        HunterShotgunAimClient.beforeUsePacket(player, hand);
    }
}
