package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleResolver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Substitutes the SparkWitch-owned corpse snapshot only at Wathe's body-role display seam. */
@Mixin(value = RoleNameRenderer.class, remap = false)
public abstract class WraithBodyRoleNameMixin {
    @ModifyExpressionValue(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Ljava/util/UUID;)Ldev/doctor4t/wathe/api/Role;"
            )
    )
    private static Role sparkwitch$useCapturedBodyRole(
            Role currentRole,
            @Local PlayerBodyEntity body
    ) {
        return WraithBodyRoleResolver.resolve(body, currentRole);
    }
}
