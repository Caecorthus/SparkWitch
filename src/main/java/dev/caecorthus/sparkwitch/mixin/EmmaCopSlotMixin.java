package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRoundComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Substitute within the existing cop budget, retaining Wathe's player selection and rotation.
 * 仅在已有警位内替换职业，保留 wathe 的选人及轮换算法。 */
@Mixin(value = ScoreboardRoleSelectorComponent.class, remap = false)
public abstract class EmmaCopSlotMixin {
    @WrapOperation(method = "assignCivilians", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRoleEnabled(Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$excludeCivilianPool(GameWorldComponent game, Role role, Operation<Boolean> original) {
        return !EmmaRules.isEmma(role) && original.call(game, role);
    }

    @WrapOperation(method = "assignVigilantes", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/Role;"))
    private Role sparkwitch$countForcedEmma(GameWorldComponent game, PlayerEntity player, Operation<Role> original) {
        Role role = original.call(game, player);
        return EmmaRules.isEmma(role) ? WatheRoles.VIGILANTE : role;
    }

    @WrapOperation(method = "assignVigilantes", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;addRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)V"))
    private void sparkwitch$replaceOneCop(GameWorldComponent game, PlayerEntity player, Role role, Operation<Void> original) {
        Role selected = role;
        if (role == WatheRoles.VIGILANTE && !EmmaRoundComponent.KEY.get(player.getWorld()).claimed()
                && game.isRoleEnabled(SparkWitchRoles.emma())) selected = SparkWitchRoles.emma();
        original.call(game, player, selected);
    }
}
