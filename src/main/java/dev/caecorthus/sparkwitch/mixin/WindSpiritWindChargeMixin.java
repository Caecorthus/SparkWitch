package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores vanilla wind-charge knockback for ordinary participants while excluding active Wraiths.
 * 为普通参赛玩家恢复原版风弹击退，同时排除 active Wraith。
 */
@Mixin(AbstractWindChargeEntity.class)
public abstract class WindSpiritWindChargeMixin {
    @Inject(method = "canHit", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$resolvePlayerHit(
            Entity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        AbstractWindChargeEntity charge = (AbstractWindChargeEntity) (Object) this;
        Entity owner = charge.getOwner();
        PlayerEntity targetPlayer = target instanceof PlayerEntity player ? player : null;
        boolean resolved = WindSpiritRules.resolveWindChargeHit(
                cir.getReturnValueZ(),
                charge instanceof WindChargeEntity,
                owner instanceof PlayerEntity player && WindSpiritRules.isActivePromotedWindSpirit(player),
                target == owner,
                targetPlayer != null,
                targetPlayer != null && targetPlayer.isAlive(),
                targetPlayer != null && GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                targetPlayer != null && targetPlayer.isSpectator(),
                WraithStateService.isActive(targetPlayer)
        );
        if (resolved != cir.getReturnValueZ()) {
            cir.setReturnValue(resolved);
        }
    }
}
