package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Grant only native police cooldown/mood exemptions, never immunity from shooting innocents.
 * 仅赋予原生警察的冷却与普通射击理智豁免，不改变误杀惩罚。 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class EmmaPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$emmaPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                    Role queriedRole, Operation<Boolean> original) {
        Role actual = game.getRole(player);
        return original.call(game, player, queriedRole) || (queriedRole == WatheRoles.VIGILANTE
                && actual != null && EmmaRules.ROLE_ID.equals(actual.identifier()));
    }
}
