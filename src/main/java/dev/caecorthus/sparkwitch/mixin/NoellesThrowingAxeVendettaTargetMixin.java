package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserves Throwing Axe behavior while allowing its owner to hit the exact bound Vendetta. */
@Mixin(ThrowingAxeEntity.class)
public abstract class NoellesThrowingAxeVendettaTargetMixin {
    @Redirect(
            method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerAliveAndSurvival(Lnet/minecraft/entity/player/PlayerEntity;)Z"
            ),
            require = 1
    )
    private boolean sparkwitch$allowBoundVendettaTarget(PlayerEntity target) {
        Entity owner = ((ThrowingAxeEntity) (Object) this).getOwner();
        return GameFunctions.isPlayerAliveAndSurvival(target)
                || owner instanceof PlayerEntity player
                && VendettaInteractionService.isBoundKillerTargetingVendetta(player, target);
    }
}
