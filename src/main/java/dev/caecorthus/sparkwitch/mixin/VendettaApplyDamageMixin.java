package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves exact-pair hit and knockback feedback without allowing vanilla health death to bypass terminal handling.
 * 保留绑定双方的受击与击退反馈，但不允许原版扣血死亡绕过仇杀终止结算。
 */
@Mixin(PlayerEntity.class)
public abstract class VendettaApplyDamageMixin {
    @Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepVendettaDamageNonLethal(
            DamageSource source,
            float amount,
            CallbackInfo ci
    ) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (VendettaInteractionService.isActiveVendetta(player)) {
            ci.cancel();
        }
    }
}
