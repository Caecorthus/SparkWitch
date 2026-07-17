package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithOwnedEffectRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Preserves Wraith-owned effects while allowing unrelated effects to clear normally.
 * 保留冤魂自有状态效果，同时允许无关效果正常清除。
 */
@Mixin(LivingEntity.class)
public abstract class WraithOwnedEffectMixin {
    @Inject(method = "removeStatusEffectInternal", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$preserveOwnedEffect(
            RegistryEntry<StatusEffect> effect,
            CallbackInfoReturnable<StatusEffectInstance> cir
    ) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target instanceof PlayerEntity player && WraithOwnedEffectRules.shouldPreserve(player, effect)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "clearStatusEffects", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$clearUnownedEffects(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (!(target instanceof PlayerEntity player) || !WraithStateService.isActive(player)) {
            return;
        }
        boolean removedAny = false;
        for (StatusEffectInstance instance : List.copyOf(target.getStatusEffects())) {
            RegistryEntry<StatusEffect> effect = instance.getEffectType();
            if (!WraithOwnedEffectRules.shouldPreserve(player, effect)) {
                removedAny |= target.removeStatusEffect(effect);
            }
        }
        cir.setReturnValue(removedAny);
    }
}
