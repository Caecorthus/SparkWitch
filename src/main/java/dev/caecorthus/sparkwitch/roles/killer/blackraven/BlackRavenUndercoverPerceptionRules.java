package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Resolves the frozen role shown for NoellesRoles Undercover without loading optional mod classes.
 * 在不直接加载可选模组类的前提下，解析卧底完成感知后冻结显示的职业。
 */
final class BlackRavenUndercoverPerceptionRules {
    private BlackRavenUndercoverPerceptionRules() {
    }

    static BlackRavenIdentitySnapshot createSnapshot(
            UUID matchId,
            UUID targetId,
            String playerName,
            Role actualRole,
            Collection<Role> registeredRoles,
            Collection<Role> rolesInMatch
    ) {
        Role displayedRole = resolveDisplayedRole(
                actualRole,
                registeredRoles,
                rolesInMatch,
                matchId,
                targetId
        );
        return new BlackRavenIdentitySnapshot(
                targetId,
                playerName,
                displayedRole.identifier().toString(),
                displayedRole.color()
        );
    }

    static Role resolveDisplayedRole(
            Role actualRole,
            Collection<Role> registeredRoles,
            Collection<Role> rolesInMatch,
            UUID matchId,
            UUID targetId
    ) {
        if (!NoellesRoleIds.isUndercover(actualRole)) {
            return actualRole;
        }

        Set<Identifier> appearedRoleIds = new HashSet<>();
        for (Role role : rolesInMatch) {
            if (role != null) {
                // Conscience keeps the assigned base killer role, so good killers reserve that role here too.
                // “良知”不会替换底层杀手职业，因此善良杀手也会在这里占用其本局已出现的职业。
                appearedRoleIds.add(role.identifier());
            }
        }
        Map<String, Role> absentKillersById = new TreeMap<>();
        for (Role role : registeredRoles) {
            if (role != null
                    && role.getFaction() == Faction.KILLER
                    && !appearedRoleIds.contains(role.identifier())) {
                absentKillersById.putIfAbsent(role.identifier().toString(), role);
            }
        }
        if (absentKillersById.isEmpty()) {
            return WatheRoles.KILLER;
        }

        List<Role> absentKillers = List.copyOf(absentKillersById.values());
        int index = Math.floorMod(Objects.hash(matchId, targetId), absentKillers.size());
        return absentKillers.get(index);
    }
}
