package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs one Insider with a drawn Corrupt Cop, called from {@code MurderGameModeMixin} after the Witch assignment and
 * before Wathe assigns civilians, so Grand Witch and Apprentice picks are never starved. {@code RoleAssigned} then fires
 * for the Insider like for every other player. Modeled on {@code HunterOrthopedistPairingService}; it never demotes or
 * re-pairs a forced Insider (C2).
 * 为已抽到的黑警配一名内应。由 {@code MurderGameModeMixin} 在魔女分配之后、Wathe 分配平民之前调用，
 * 因此不会挤占大魔女与预备魔女的名额；之后内应与其他玩家一样触发 {@code RoleAssigned}。
 * 仿照 {@code HunterOrthopedistPairingService}；从不降级或重新配对强制指定的内应（C2）。
 */
public final class InsiderAssignmentService {
    private InsiderAssignmentService() {
    }

    public static void assignAfterNeutralsBeforeCivilians(
            ServerWorld world,
            GameWorldComponent gameComponent,
            List<ServerPlayerEntity> players
    ) {
        Role insider = SparkWitchRoles.insider();
        int corruptCopCount = 0;
        int insiderCount = 0;
        int killerCount = 0;
        List<ServerPlayerEntity> available = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            Role role = gameComponent.getRole(player);
            if (role == null || role == WatheRoles.NO_ROLE) {
                available.add(player);
                continue;
            }
            if (InsiderParticipation.isCorruptCopRole(role)) {
                corruptCopCount++;
            }
            if (InsiderParticipation.isInsiderRole(role)) {
                insiderCount++;
            }
            // Killer-team roles actually assigned so far, forced killers included (C1).
            // 目前实际分配到的杀手阵营身份，含强制指定的杀手（C1）。
            if (role.canUseKiller()) {
                killerCount++;
            }
        }

        if (InsiderPairingRules.pairingAction(
                gameComponent.isRoleEnabled(insider),
                corruptCopCount,
                insiderCount,
                killerCount,
                available.size()
        ) == InsiderPairingRules.PairingAction.ASSIGN) {
            ServerPlayerEntity selected = available.get(world.getRandom().nextInt(available.size()));
            gameComponent.addRole(selected, insider);
        }
    }
}
