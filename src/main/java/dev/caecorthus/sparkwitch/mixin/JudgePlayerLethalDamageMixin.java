package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeVanillaDamage;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** After vanilla reductions, before health reaches zero; nonlethal damage is untouched.
 * 原版减伤结算后、生命归零前拦截；非致死伤害不变。 */
@Mixin(PlayerEntity.class)
public abstract class JudgePlayerLethalDamageMixin {
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;setHealth(F)V"))
    private void sparkwitch$guardLethalHealth(PlayerEntity victim, float health, Operation<Void> original,
                                             DamageSource source, float amount) {
        if (!JudgeVanillaDamage.blocksTransition(victim, health, source)) original.call(victim, health);
    }
}
