package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.command.ForceAbilityCommand;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Shared command-time validation for SparkWitch ability and Wathe role locks.
 * SparkWitch 能力锁定与 Wathe 身份锁定的命令期共用校验入口。
 */
public final class WitchSkillLockValidationService {
    private WitchSkillLockValidationService() {
    }

    public static Role forcedRoleFor(ServerCommandSource source, ServerPlayerEntity player) {
        ScoreboardRoleSelectorComponent selector =
                ScoreboardRoleSelectorComponent.KEY.get(source.getServer().getScoreboard());
        return selector.getForcedRoleForPlayer(player.getUuid());
    }

    public static RoleConflict findForcedRoleConflict(
            ServerCommandSource source,
            ServerPlayerEntity player,
            WitchSkillDefinition skill
    ) {
        return findRoleConflict(skill, forcedRoleFor(source, player));
    }

    public static RoleConflict findForcedSkillRoleConflict(ServerPlayerEntity player, Role role) {
        Identifier forcedSkillId = WitchPlayerComponent.KEY.get(player).getForcedSkillId();
        return findForcedSkillRoleConflict(forcedSkillId, role);
    }

    public static RoleConflict findForcedSkillRoleConflict(Identifier forcedSkillId, Role role) {
        if (forcedSkillId == null) {
            return null;
        }
        WitchSkillDefinition skill = WitchSkillRegistry.get(forcedSkillId);
        return skill == null ? null : findRoleConflict(skill, role);
    }

    public static RoleConflict findRoleConflict(WitchSkillDefinition skill, Role role) {
        Objects.requireNonNull(skill, "skill");
        if (isUnknownRole(role)) {
            return null;
        }
        return skill.canSelect(new WitchSkillSelectionContext(null, null, null, role))
                ? null
                : new RoleConflict(skill, role);
    }

    public static String forceAbilityConflictMessage(RoleConflict conflict) {
        return "无法锁定，因为 " + ForceAbilityCommand.formatSkillIdForCommand(conflict.skill().id()) + " 与 "
                + formatRoleId(conflict.role()) + " 冲突。";
    }

    public static Role findForceableRole(String roleName) {
        for (Role role : WatheRoles.ROLES) {
            if (!WatheRoles.SPECIAL_ROLES.contains(role) && role.identifier().getPath().equals(roleName)) {
                return role;
            }
        }
        return null;
    }

    private static String formatRoleId(Role role) {
        return role.identifier().getPath();
    }

    private static boolean isUnknownRole(Role role) {
        return role == null || role == WatheRoles.NO_ROLE;
    }

    public record RoleConflict(WitchSkillDefinition skill, Role role) {
    }
}
