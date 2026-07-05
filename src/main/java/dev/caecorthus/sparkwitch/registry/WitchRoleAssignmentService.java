package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleSelectionContext;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WitchRoleAssignmentService {
    private WitchRoleAssignmentService() {
    }

    public static int assignAfterNeutralsBeforeCivilians(
            ServerWorld world,
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> players
    ) {
        WitchRoleCounts.Counts counts = WitchRoleCounts.forPlayerCount(players.size());
        if (counts.grandWitches() == 0 && counts.apprenticeWitches() == 0) {
            return 0;
        }

        List<ServerPlayerEntity> availablePlayers = availablePlayers(gameComponent, players);
        if (availablePlayers.isEmpty()) {
            return 0;
        }
        Collections.shuffle(availablePlayers, new java.util.Random(world.getRandom().nextLong()));

        Role grandWitch = SparkWitchRoles.grandWitch();
        Role accomplice = SparkWitchRoles.accomplice();
        Role apprenticeWitch = SparkWitchRoles.apprenticeWitch();
        RoleSelectionContext roleContext = roleSelectionContext(world, gameComponent, players);

        int assigned = 0;
        boolean grandPresent = countRole(gameComponent, players, grandWitch) > 0;

        int grandRemaining = counts.grandWitches() - countRole(gameComponent, players, grandWitch);
        if (grandRemaining > 0 && isEligible(gameComponent, roleContext, grandWitch)) {
            assigned += assignRole(gameComponent, availablePlayers, grandWitch, grandRemaining);
            grandPresent = grandPresent || countRole(gameComponent, players, grandWitch) > 0;
        }

        // Accomplices belong to the custom witch faction, so they only auto-fill when the Grand Witch exists.
        // 共犯属于魔女阵营；只有大魔女已存在或本轮成功生成时，才自动补入共犯。
        if (grandPresent && isEligible(gameComponent, roleContext, accomplice)) {
            int accompliceRemaining = counts.accomplices() - countRole(gameComponent, players, accomplice);
            assigned += assignRole(gameComponent, availablePlayers, accomplice, accompliceRemaining);
        }

        // Apprentice Witch is a civilian role and follows the >=24 rule directly, independent of actual Grand Witch assignment.
        // 预备魔女是好人职业，只按 >=24 的人数规则刷新，不依赖大魔女是否真的被分到。
        if (isEligible(gameComponent, roleContext, apprenticeWitch)) {
            int apprenticeRemaining = counts.apprenticeWitches() - countRole(gameComponent, players, apprenticeWitch);
            assigned += assignRole(gameComponent, availablePlayers, apprenticeWitch, apprenticeRemaining);
        }

        return assigned;
    }

    private static List<ServerPlayerEntity> availablePlayers(
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> players
    ) {
        List<ServerPlayerEntity> available = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            Role role = gameComponent.getRole(player);
            if (role == null || role == WatheRoles.NO_ROLE) {
                available.add(player);
            }
        }
        return available;
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

    private static boolean isEligible(GameWorldComponent gameComponent, RoleSelectionContext roleContext, Role role) {
        return gameComponent.isRoleEnabled(role) && role.shouldAppear(roleContext);
    }

    private static int assignRole(
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> availablePlayers,
            Role role,
            int desiredCount
    ) {
        int assigned = 0;
        while (desiredCount > 0 && !availablePlayers.isEmpty()) {
            ServerPlayerEntity player = availablePlayers.removeFirst();
            gameComponent.addRole(player, role);
            assigned++;
            desiredCount--;
        }
        return assigned;
    }

    private static RoleSelectionContext roleSelectionContext(
            ServerWorld world,
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> players
    ) {
        int totalPlayerCount = players.size();
        int targetKillerCount = (int) Math.floor((double) totalPlayerCount / gameComponent.getKillerDividend());
        int targetNeutralCount = (int) Math.floor((double) totalPlayerCount / gameComponent.getNeutralDividend());
        int targetVigilanteCount = (int) Math.floor((double) totalPlayerCount / gameComponent.getVigilanteDividend());
        return new RoleSelectionContext(
                world,
                gameComponent,
                List.copyOf(players),
                totalPlayerCount,
                targetKillerCount,
                targetNeutralCount,
                targetVigilanteCount
        );
    }
}
