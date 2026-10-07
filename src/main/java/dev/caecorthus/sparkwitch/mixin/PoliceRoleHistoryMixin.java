package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.judge.PoliceSlotAssignmentService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.RoleHistoryComponent;
import dev.doctor4t.wathe.game.rotation.RoleCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Police variants repay police rotation debt without changing their actual role identity.
 * 警职变体偿还警位轮换债务，不改变其真实身份。 */
@Mixin(RoleHistoryComponent.class)
public abstract class PoliceRoleHistoryMixin {
    @Inject(method = "categoryOf", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$classifyPoliceVariant(Role role, CallbackInfoReturnable<RoleCategory> cir) {
        if (PoliceSlotAssignmentService.isVariant(role)) {
            cir.setReturnValue(RoleCategory.VIGILANTE);
        }
    }
}
