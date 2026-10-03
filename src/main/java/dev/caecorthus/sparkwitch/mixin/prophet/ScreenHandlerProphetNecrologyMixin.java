package dev.caecorthus.sparkwitch.mixin.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetNecrologyRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Necrology inside its owner's own inventory slots. Server-authoritative: a rejected click is cancelled
 * before any slot changes and the client's predicted move is overwritten by a content resync.
 * 使亡者名录只留在持有者自身的背包栏位中。由服务端裁定：被拒绝的点击在任何栏位变化之前取消，
 * 并通过内容重同步覆盖客户端的预测移动。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerProphetNecrologyMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepProphetNecrology(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayerEntity
                && ProphetNecrologyRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
