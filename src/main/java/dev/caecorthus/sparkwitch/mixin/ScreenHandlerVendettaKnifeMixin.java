package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the revenge knife inside its owner's own inventory slots. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerVendettaKnifeMixin {
    @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockVendettaKnifeTransfer(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayerEntity
                && VendettaKnifeInventoryRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
