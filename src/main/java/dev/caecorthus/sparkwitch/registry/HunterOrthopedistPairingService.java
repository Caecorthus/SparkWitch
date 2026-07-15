package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces Hunter's one-way Orthopedist pairing after killer assignment and before civilian assignment.
 * 在杀手分配后、好人分配前落实“猎人必带一名骨科大夫”的单向配对。
 */
public final class HunterOrthopedistPairingService {
    private HunterOrthopedistPairingService() {
    }

    public static void ensurePairBeforeCivilians(
            ServerWorld world,
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> players
    ) {
        Role hunter = SparkWitchRoles.hunter();
        Role orthopedist = SparkWitchRoles.orthopedist();
        int hunterCount = countRole(gameComponent, players, hunter);
        int orthopedistCount = countRole(gameComponent, players, orthopedist);

        List<ServerPlayerEntity> available = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            Role role = gameComponent.getRole(player);
            if (role == null || role == WatheRoles.NO_ROLE) {
                available.add(player);
            }
        }

        switch (HunterOrthopedistPairingRules.pairingAction(
                hunterCount,
                orthopedistCount,
                available.size(),
                gameComponent.isRoleEnabled(orthopedist)
        )) {
            case NONE -> {
            }
            case ASSIGN_ORTHOPEDIST -> {
                ServerPlayerEntity selected = available.get(world.getRandom().nextInt(available.size()));
                gameComponent.addRole(selected, orthopedist);
            }
            case DEMOTE_HUNTERS -> {
                // Forced-role setups can consume every slot; preserve the killer count without leaving an invalid pair.
                // 强制身份可能占满所有位置；保留杀手数量，但不留下缺少骨科大夫的非法配对。
                for (ServerPlayerEntity player : players) {
                    if (gameComponent.getRole(player) == hunter) {
                        gameComponent.addRole(player, WatheRoles.KILLER);
                    }
                }
                SparkWitch.LOGGER.warn("Hunter assignment fell back to Killer because no Orthopedist slot was available");
            }
        }
    }

    private static int countRole(GameWorldComponent gameComponent, List<ServerPlayerEntity> players, Role role) {
        int count = 0;
        for (ServerPlayerEntity player : players) {
            if (gameComponent.getRole(player) == role) {
                count++;
            }
        }
        return count;
    }
}
