package dev.caecorthus.sparkwitch.mixin.wraith;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Active Wraiths ignore secondary damage; train fall is handled explicitly.
 * 激活冤魂忽略二次伤害；掉出列车由生命周期显式处理。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class WraithDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockWraithDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (WraithStateService.isActive((ServerPlayerEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
