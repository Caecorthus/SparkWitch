package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.judge.PoliceSlotAssignmentService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Preserve host slot/debt selection and force-role bypass; only replace random Vigilante variants.
 * 保留宿主警位／轮换选人与硬锁例外，只替换随机义警变体。 */
@Mixin(ScoreboardRoleSelectorComponent.class)
public abstract class PoliceSlotAssignmentMixin {
    @WrapOperation(method = "assignVigilantes", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/Role;"))
    private Role sparkwitch$countForcedPoliceVariants(GameWorldComponent game, PlayerEntity player, Operation<Role> original) {
        Role role = original.call(game, player);
        return PoliceSlotAssignmentService.isVariant(role) ? WatheRoles.VIGILANTE : role;
    }

    @WrapOperation(method = "assignVigilantes", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;addRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)V"))
    private void sparkwitch$choosePoliceVariant(GameWorldComponent game, PlayerEntity player, Role role,
                                               Operation<Void> original,
                                               @Local(argsOnly = true) ServerWorld world,
                                               @Local(argsOnly = true) List<ServerPlayerEntity> players) {
        Role selected = role;
        if (role == WatheRoles.VIGILANTE) {
            ScoreboardRoleSelectorComponent selector = (ScoreboardRoleSelectorComponent) (Object) this;
            selected = PoliceSlotAssignmentService.choose(selector.createSelectionContext(world, game, players));
        }
        original.call(game, player, selected);
    }

    @WrapOperation(method = "assignCivilians", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRoleEnabled(Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$excludePoliceVariantsFromCivilianPool(GameWorldComponent game, Role role,
                                                                    Operation<Boolean> original) {
        return !PoliceSlotAssignmentService.isVariant(role) && original.call(game, role);
    }
}
