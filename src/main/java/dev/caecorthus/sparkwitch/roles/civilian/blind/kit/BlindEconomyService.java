package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Blind money (D8), Angler rule: the balance is visible to a living participant Blind, set to 0 when the role is
 * assigned, and every completed task pays {@link BlindRules#TASK_REWARD}. Like the Angler there is no SparkTraits
 * Impostor check (task income stacks with Impostor income; SparkTraits itself no longer rolls the Impostor for the
 * Blind).
 * 盲人金币（D8），沿用钓鱼佬规则：存活参与的盲人可见余额，分配职业时余额置 0，每完成一个任务获得
 * {@link BlindRules#TASK_REWARD}。与钓鱼佬相同，不检查 SparkTraits 内鬼（任务收入与内鬼收入叠加；SparkTraits
 * 本身已不再为盲人抽取内鬼）。
 */
public final class BlindEconomyService {
    private static boolean registered;

    private BlindEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(player -> player != null
                ? BlindKitRules.moneyVisibility(isLivingParticipant(player),
                        BlindRules.isBlind(GameWorldComponent.KEY.get(player.getWorld()).getRole(player)))
                : null);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && BlindRules.isBlind(role)) {
                PlayerShopComponent.KEY.get(serverPlayer).setBalance(0);
            }
        });
        TaskComplete.EVENT.register((player, task) -> {
            if (BlindRules.isBlind(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
                PlayerShopComponent.KEY.get(player).addToBalance(BlindRules.TASK_REWARD);
            }
        });
    }

    /** Both sides: alive, connected, playing and not a spectator or creative player. / 双端：存活、在线、参与且非旁观/创造。 */
    private static boolean isLivingParticipant(PlayerEntity player) {
        return player.isAlive()
                && !(player instanceof ServerPlayerEntity serverPlayer && serverPlayer.isDisconnected())
                && GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player);
    }
}
