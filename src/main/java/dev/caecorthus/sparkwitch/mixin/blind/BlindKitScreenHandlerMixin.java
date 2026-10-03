package dev.caecorthus.sparkwitch.mixin.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the White Cane and ComTac VIII inside their holder's own inventory slots (head slot included). Server
 * authoritative: a rejected click is cancelled before any slot changes and the client's predicted move is overwritten
 * by a content resync.
 * 使盲杖与 ComTac VIII 只留在持有者自身的背包栏位中（含头部槽）。由服务端裁定：被拒绝的点击在任何栏位变化之前取消，
 * 并通过内容重同步覆盖客户端的预测移动。
 */
@Mixin(ScreenHandler.class)
public abstract class BlindKitScreenHandlerMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepBlindKit(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayerEntity
                && BlindInventoryRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
