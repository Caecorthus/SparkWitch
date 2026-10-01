package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Optional SparkStrength seam: keeps the killer-team purse on the stashed Black Raven wallet's side of a
 * disguised Raven. While disguised the purse neither funds nor is debited by disguise purchases, and income
 * landing in the disguise wallet is not reported as killer income; routed Raven-wallet kill rewards are
 * reported by BlackRavenDisguiseEconomy instead. Name-only selectors, every injector {@code require = 0}:
 * an absent target or selector means disguise purchases may draw on the purse (degraded, never lost).
 * 可选 SparkStrength 接缝：让杀手团队资金只与伪装黑羽鸦存档中的黑羽鸦钱包相关。伪装期间团队资金既不资助
 * 也不承担伪装购买，进入伪装钱包的收入也不作为杀手收入上报；转入黑羽鸦钱包的击杀奖励由
 * BlackRavenDisguiseEconomy 上报。仅按方法名选择，所有注入 require = 0：目标或选择器缺失时伪装购买
 * 可能动用团队资金（降级，资金不会丢失）。
 */
@Pseudo
@Mixin(targets = "annina.sparkstrength.role.economy.KillerTeamEconomyService", remap = false)
public abstract class KillerTeamEconomyBlackRavenWalletMixin {
    @WrapMethod(method = "recordIncome", require = 0)
    private static void sparkwitch$reportOnlyKillerIncome(
            PlayerEntity player,
            long actualIncome,
            Operation<Void> original
    ) {
        if (BlackRavenDisguiseEconomy.acceptsKillerTeamContribution(player)) {
            original.call(player, actualIncome);
        }
    }

    @WrapMethod(method = "availableForPurchase", require = 0)
    private static int sparkwitch$personalOnlyWhileDisguised(
            PlayerEntity player,
            int personalBalance,
            Operation<Integer> original
    ) {
        return BlackRavenDisguiseService.routesKillerIncome(player)
                ? personalBalance
                : original.call(player, personalBalance);
    }

    @WrapMethod(method = "personalBalanceAfterPurchase", require = 0)
    private static int sparkwitch$noPurseDebitWhileDisguised(
            PlayerEntity player,
            int personalBalance,
            int price,
            Operation<Integer> original
    ) {
        return BlackRavenDisguiseService.routesKillerIncome(player)
                ? personalBalance - price
                : original.call(player, personalBalance, price);
    }
}
