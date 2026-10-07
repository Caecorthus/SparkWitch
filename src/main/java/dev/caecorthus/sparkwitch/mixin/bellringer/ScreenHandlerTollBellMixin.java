package dev.caecorthus.sparkwitch.mixin.bellringer;

import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the bound bell inside its owner's own inventory slots. Server-authoritative: a rejected click is
 * cancelled and the client's predicted move is overwritten by a content resync.
 * 使绑定之钟只留在拥有者自身的背包栏位中。由服务端裁定：被拒绝的点击会被取消，并通过内容重同步覆盖客户端的预测移动。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerTollBellMixin {
    @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockTollBellTransfer(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayerEntity
                && BellRingerInventoryRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
