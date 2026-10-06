package dev.caecorthus.sparkwitch.mixin.recruitment;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A held recruit takes no vanilla damage (as the Wraith guard does), except sources that bypass vanilla
 * invulnerability ({@code /kill}, the void), which Wathe turns into its own {@code wathe:vanilla_death}.
 * 被定身的新共犯不承受原版伤害（与冤魂守卫相同），绕过原版无敌的来源（{@code /kill}、虚空）除外，
 * Wathe 会把它们转为自己的 {@code wathe:vanilla_death}。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityRecruitmentHoldDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockHeldRecruitDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (RecruitmentHold.blocksDamage((ServerPlayerEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }
}
