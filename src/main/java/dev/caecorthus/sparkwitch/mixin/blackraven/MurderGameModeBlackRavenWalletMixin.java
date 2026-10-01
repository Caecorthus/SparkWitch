package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes Wathe killer passive income (cap check and credit) of a disguised Raven to its stashed Black Raven wallet.
 * Both invokes sit inside the canUseKillerFeatures branch of tickServerGameLoop (1.5.6: getBalance @115,
 * addToBalance @131); every other player keeps Wathe's live-wallet behaviour.
 * 将伪装黑羽鸦的 Wathe 杀手被动收入（上限检查与入账）转入其存档中的黑羽鸦钱包。两处调用均位于
 * tickServerGameLoop 的 canUseKillerFeatures 分支（1.5.6：getBalance @115、addToBalance @131）；其他玩家保持原逻辑。
 */
@Mixin(value = MurderGameMode.class, remap = false)
public abstract class MurderGameModeBlackRavenWalletMixin {
    @WrapOperation(
            method = "tickServerGameLoop",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;getBalance()I"),
            require = 1,
            allow = 1
    )
    private int sparkwitch$capAgainstRavenWallet(PlayerShopComponent shop, Operation<Integer> original) {
        PlayerEntity owner = BlackRavenDisguiseEconomy.killerIncomeOwner(shop);
        return owner != null ? BlackRavenDisguiseService.ravenWalletBalance(owner) : original.call(shop);
    }

    @WrapOperation(
            method = "tickServerGameLoop",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$creditRavenWallet(PlayerShopComponent shop, int amount, Operation<Void> original) {
        // Timed receipt: never a SparkStrength purse contribution. / 定时收入：不计入 SparkStrength 团队资金。
        if (!BlackRavenDisguiseEconomy.creditKillerIncome(shop, amount, false)) {
            original.call(shop, amount);
        }
    }
}
