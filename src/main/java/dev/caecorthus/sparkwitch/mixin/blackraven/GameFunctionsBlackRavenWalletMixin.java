package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes killPlayer kill rewards, teammate shares and the civilian-death pool of a disguised Raven to its
 * stashed Black Raven wallet; other players and disguise income keep the live wallet. In the 1.5.6 jar all
 * three addToBalance invokes of killPlayer are killer-team income (@389 killer reward after
 * canUseKillerFeatures, @512 getAllKillerTeamPlayers share, @795 civilian-death pool), so each is routed only
 * when the credited owner is a disguised, bound Raven.
 * 将 killPlayer 中伪装黑羽鸦的击杀奖励、队友分成与平民死亡奖池转入其存档黑羽鸦钱包；其他玩家与伪装收入仍用当前钱包。
 * 1.5.6 中 killPlayer 的三处 addToBalance 均为杀手阵营收入（@389 击杀奖励、@512 队友分成、@795 平民死亡奖池），
 * 仅当入账者为已绑定的伪装黑羽鸦时才转入。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class GameFunctionsBlackRavenWalletMixin {
    @WrapOperation(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V"),
            require = 3,
            allow = 3
    )
    private static void sparkwitch$creditRavenWallet(PlayerShopComponent shop, int amount, Operation<Void> original) {
        // Action rewards feed the SparkStrength purse like a normal killer receipt. / 行动奖励与普通杀手一样计入团队资金。
        if (!BlackRavenDisguiseEconomy.creditKillerIncome(shop, amount, true)) {
            original.call(shop, amount);
        }
    }
}
