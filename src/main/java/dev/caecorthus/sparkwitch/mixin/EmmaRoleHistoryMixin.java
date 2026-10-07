package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.RoleHistoryComponent;
import dev.doctor4t.wathe.game.rotation.RoleCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RoleHistoryComponent.class, remap = false)
public abstract class EmmaRoleHistoryMixin {
    @Inject(method = "categoryOf", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$copHistory(Role role, CallbackInfoReturnable<RoleCategory> cir) {
        if (EmmaRules.isEmma(role)) cir.setReturnValue(RoleCategory.VIGILANTE);
    }
}
