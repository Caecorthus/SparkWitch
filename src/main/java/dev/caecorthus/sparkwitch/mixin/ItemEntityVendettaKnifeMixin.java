package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeInventoryRules;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deletes externally-created revenge-knife drops before any player can pick them up. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityVendettaKnifeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$discardVendettaKnifeDrop(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (VendettaKnifeInventoryRules.isKnife(self.getStack())) {
            self.discard();
            ci.cancel();
        }
    }
}
