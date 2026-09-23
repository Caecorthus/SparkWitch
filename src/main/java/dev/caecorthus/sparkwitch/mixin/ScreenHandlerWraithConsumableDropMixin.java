package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithConsumableInventoryRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerWraithConsumableDropMixin {
    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Shadow
    public abstract ItemStack getCursorStack();

    @Shadow
    public abstract void sendContentUpdates();

    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$blockRestrictedWraithConsumableDrop(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack candidate = ItemStack.EMPTY;
        if (actionType == SlotActionType.THROW
                && getCursorStack().isEmpty()
                && slotIndex >= 0
                && slotIndex < slots.size()) {
            candidate = slots.get(slotIndex).getStack();
        } else if (actionType == SlotActionType.PICKUP
                && slotIndex == -999
                && (button == 0 || button == 1)) {
            candidate = getCursorStack();
        }

        if (WraithConsumableInventoryRules.blocksDrop(
                WraithStateService.isRestricted(serverPlayer),
                candidate
        )) {
            sendContentUpdates();
            ci.cancel();
        }
    }
}
