package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Active Wraiths cannot take secondary damage; train fall is handled explicitly.
 * 激活的冤魂不承受二次伤害；掉出列车由独立生命周期处理。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class WraithDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockWraithDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (WraithStateService.isActive(player)
                && !VendettaInteractionService.isDamageFromBoundKiller(player, source)) {
            cir.setReturnValue(false);
        }
    }
}
