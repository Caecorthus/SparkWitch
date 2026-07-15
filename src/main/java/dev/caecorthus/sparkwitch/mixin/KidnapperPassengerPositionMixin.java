package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperPassengerPositioning;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thin external positioning seam; all geometry stays in the owning helper. / 外部位置钩子保持轻薄，几何由专属类负责。 */
@Mixin(Entity.class)
public abstract class KidnapperPassengerPositionMixin {
    @Inject(method = "getPassengerRidingPos", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$positionDraggedBody(
            Entity passenger,
            CallbackInfoReturnable<Vec3d> cir
    ) {
        if (!((Object) this instanceof PlayerEntity carrier)
                || !(passenger instanceof PlayerBodyEntity)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(carrier.getWorld()).getRole(carrier);
        if (KidnapperRules.isKidnapper(role)) {
            cir.setReturnValue(KidnapperPassengerPositioning.behind(carrier));
        }
    }
}
