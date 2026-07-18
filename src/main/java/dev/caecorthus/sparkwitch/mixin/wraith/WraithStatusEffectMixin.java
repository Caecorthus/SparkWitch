package dev.caecorthus.sparkwitch.mixin.wraith;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks player-sourced effects across either side of Wraith isolation.
 * 阻断冤魂隔离关系任一方向上的玩家来源状态效果。
 */
@Mixin(LivingEntity.class)
public abstract class WraithStatusEffectMixin {
    @Inject(
            method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$blockPlayerEffectOnWraith(
            StatusEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target instanceof PlayerEntity targetPlayer
                && source instanceof PlayerEntity actor
                && actor != targetPlayer
                && (WraithStateService.isActive(actor) || WraithStateService.isActive(targetPlayer))) {
            cir.setReturnValue(false);
        }
    }
}
