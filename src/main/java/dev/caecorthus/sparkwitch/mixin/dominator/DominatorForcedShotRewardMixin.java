package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotRewards;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes only the forced shooter's own balance while the forced shot and its scheduled consequences run
 * ({@link DominatorForcedShotRewards#freezesBalanceOf}): no kill money, bounty or penalty of their own. Every other
 * player's shop settles normally. Both entry points are guarded, {@code addToBalance} so no other mod's hook on it
 * sees a phantom reward, {@code setBalance} for direct writers.
 * 仅在强制开枪及其排定后果运行期间冻结被强制开枪者自己的余额：不得到自己的击杀奖金、悬赏，也不承担扣罚。其他所有玩家的商店
 * 照常结算。两个入口都拦截：{@code addToBalance} 使其他模组在其上的钩子看不到虚假奖励，{@code setBalance} 覆盖直接写入者。
 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class DominatorForcedShotRewardMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "addToBalance", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$freezeForcedShooterReward(int amount, CallbackInfo ci) {
        if (DominatorForcedShotRewards.freezesBalanceOf(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "setBalance", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$freezeForcedShooterBalance(int amount, CallbackInfo ci) {
        if (DominatorForcedShotRewards.freezesBalanceOf(player)) {
            ci.cancel();
        }
    }
}
