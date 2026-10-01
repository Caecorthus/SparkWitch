package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optional SparkStrength seam: the Engineer power-restoration share is paid to every alive raw killer, so a
 * disguised Raven's share is killer income and goes to its stashed Black Raven wallet (amendment W); every other
 * killer keeps the live-wallet credit. Name-only selector, {@code require = 0}: an absent target leaves the share
 * in the live disguise wallet (degraded, never lost).
 * 可选 SparkStrength 接缝：工程师恢复电力的分成会付给所有存活的真实杀手，因此伪装黑羽鸦的分成属于杀手收入，
 * 记入其存档中的黑羽鸦钱包（修订 W）；其他杀手仍记入当前钱包。仅按方法名选择，require = 0：目标缺失时分成
 * 留在当前伪装钱包（降级，资金不会丢失）。
 */
@Pseudo
@Mixin(targets = "annina.sparkstrength.role.engineer.EngineerPowerRestorationService", remap = false)
public abstract class EngineerPowerRestorationBlackRavenWalletMixin {
    @WrapOperation(
            method = "distributeRestorationCost",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V"),
            require = 0
    )
    private static void sparkwitch$creditRavenWallet(PlayerShopComponent shop, int amount, Operation<Void> original) {
        // An un-suppressed addToBalance feeds the SparkStrength purse, so the routed share does too.
        // 未被抑制的 addToBalance 会计入 SparkStrength 团队资金，因此转入的分成同样计入。
        if (!BlackRavenDisguiseEconomy.creditKillerIncome(shop, amount, true)) {
            original.call(shop, amount);
        }
    }
}
