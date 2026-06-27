package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side bridge between Wathe events and SparkWitch mana state.
 * 服务端事件到 SparkWitch 魔力状态的桥接层。
 */
public final class WitchManaService {
    private WitchManaService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (WitchManaRules.isManaRole(role)) {
            component.initializeMana();
        } else {
            component.clearMana();
        }
    }

    public static void onTaskComplete(ServerPlayerEntity player, PlayerMoodComponent.Task taskType) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        Role role = gameComponent.getRole(player);
        int reward = WitchManaRules.taskReward(role);
        if (reward > 0) {
            awardMana(player, reward);
        }
    }

    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        Role killerRole = gameComponent.getRole(killer);
        Role victimRole = gameComponent.getRole(victim);
        int reward = WitchManaRules.killReward(killerRole, victimRole);
        if (reward > 0) {
            awardMana(killer, reward);
        }

        int grandWitchReward = WitchManaRules.grandWitchRewardForAccompliceKill(killerRole, victimRole);
        if (grandWitchReward <= 0) {
            return;
        }
        for (ServerPlayerEntity player : victim.getServerWorld().getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            if (gameComponent.getRole(player) == SparkWitchRoles.grandWitch()) {
                awardMana(player, grandWitchReward);
            }
        }
    }

    private static void awardMana(ServerPlayerEntity player, int amount) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.hasManaSystem()) {
            component.initializeMana();
        }
        component.addMana(amount);
    }
}
