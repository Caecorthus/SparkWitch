package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.economy.WitchEconomyService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * SparkFactionAPI economy policy and starting loadout assignment for the Witch faction.
 * 魔女阵营的 SparkFactionAPI 经济策略与开局经济/负载分配。
 */
public final class WitchFactionEconomyPolicy {
    private WitchFactionEconomyPolicy() {
    }

    static Boolean economyDecision(
            PlayerEntity player,
            FactionEconomyPolicy.RewardKind rewardKind,
            GameWorldComponent gameComponent
    ) {
        return WitchFactionRules.economyDecision(gameComponent.getRole(player), rewardKind);
    }

    /**
     * Applies role-start money and items without owning non-economy cleanup.
     * 应用身份开局金钱和物品，但不接管非经济清理逻辑。
     */
    static void assignStartingLoadout(ServerPlayerEntity player, Role role) {
        if (WitchFactionRules.isGrandWitch(role)) {
            GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
            PlayerShopComponent.KEY.get(player).setBalance(
                    WitchEconomyService.killerStyleStartingMoney(player, gameComponent)
            );
            player.giveItemStack(new ItemStack(WatheItems.KNIFE));
            return;
        }
        if (WitchFactionRules.isAccomplice(role)) {
            GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
            PlayerShopComponent.KEY.get(player).setBalance(
                    WitchEconomyService.accompliceStartingMoney(player, gameComponent)
            );
        }
    }
}
