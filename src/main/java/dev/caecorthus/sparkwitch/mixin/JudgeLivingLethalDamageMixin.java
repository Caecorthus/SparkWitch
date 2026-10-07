package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeVanillaDamage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Covers inherited/super damage without denying ordinary nonlethal damage. / 覆盖父类伤害路径，不禁止非致死伤害。 */
@Mixin(LivingEntity.class)
public abstract class JudgeLivingLethalDamageMixin {
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;setHealth(F)V"))
    private void sparkwitch$guardLethalHealth(LivingEntity victim, float health, Operation<Void> original,
                                             DamageSource source, float amount) {
        if (!JudgeVanillaDamage.blocksTransition(victim, health, source)) original.call(victim, health);
    }
}
