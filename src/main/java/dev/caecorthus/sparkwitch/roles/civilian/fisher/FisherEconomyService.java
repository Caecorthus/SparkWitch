package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Angler money: visible balance, starting balance, task money. / 钓鱼佬经济：余额可见、开局余额、任务金币。 */
public final class FisherEconomyService {
    private static boolean registered;

    private FisherEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(player -> player != null
                ? moneyVisibility(FisherTargeting.isLivingParticipant(player),
                        GameWorldComponent.KEY.get(player.getWorld()).getRole(player)) : null);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && FisherRules.isFisher(role)) {
                PlayerShopComponent.KEY.get(serverPlayer).setBalance(FisherRules.INITIAL_MONEY);
            }
        });
        TaskComplete.EVENT.register((player, task) -> {
            if (FisherRules.isFisher(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
                // Owner rule: task-role income stacks with SparkTraits' Impostor income.
                // 所有者规则：任务职业金币与 SparkTraits 的内鬼金币叠加。
                PlayerShopComponent.KEY.get(player).addToBalance(FisherRules.TASK_MONEY_REWARD);
            }
        });
    }

    static @Nullable CanSeeMoney.Result moneyVisibility(boolean livingParticipant, @Nullable Role role) {
        return livingParticipant && FisherRules.isFisher(role) ? CanSeeMoney.Result.ALLOW : null;
    }
}
