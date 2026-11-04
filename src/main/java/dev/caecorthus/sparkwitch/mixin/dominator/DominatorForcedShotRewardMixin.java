package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotRewards;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses every kill-money addition made synchronously by a forced shot. / 压制强制开枪同步发放的所有击杀奖金。 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class DominatorForcedShotRewardMixin {
    @Inject(method = "addToBalance", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$suppressForcedShotReward(int amount, CallbackInfo ci) {
        if (DominatorForcedShotRewards.isSuppressing()) {
            ci.cancel();
        }
    }
}
