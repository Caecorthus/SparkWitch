package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStunClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knife-safety guard: when the stun makes the use key read as released, vanilla would release the held item, and
 * Wathe's knife sends its stab on release. While stunned the item is cleared instead, sending nothing (the server
 * already cleared its copy when the stun was applied).
 * 刀击安全防护：眩晕使使用键视为松开时，原版会松开手持物品，而 Wathe 的刀会在松开时发出刺击。
 * 眩晕期间改为直接清除手持使用状态，不发送任何数据包（施加眩晕时服务端已清除其副本）。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ControlExpertStunInteractionMixin {
    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$clearInsteadOfRelease(PlayerEntity player, CallbackInfo ci) {
        if (ControlExpertStunClient.isLocalStunned()) {
            player.clearActiveItem();
            ci.cancel();
        }
    }
}
