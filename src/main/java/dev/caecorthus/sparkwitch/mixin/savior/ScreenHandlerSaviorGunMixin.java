package dev.caecorthus.sparkwitch.mixin.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorGunPickupRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Container clicks can move a gun without insertStack; this refuses the same moves for the Savior. / 容器点击绕过 insertStack，这里同样拒绝。 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSaviorGunMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$blockSaviorGunSlotClick(int slotIndex, int button, SlotActionType actionType,
                                                    PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (SaviorGunPickupRules.blocksSlotClick(handler, player, slotIndex, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
