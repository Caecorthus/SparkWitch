package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.skill.WitchSkillLockValidationService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.command.ForceRoleCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Filters forced roles that conflict with already locked SparkWitch abilities.
 * 过滤与已锁定 SparkWitch 能力冲突的 Wathe 强制身份目标。
 */
@Mixin(value = ForceRoleCommand.class, priority = 1500, remap = false)
public abstract class ForceRoleCommandMixin {
    @ModifyVariable(method = "execute", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private static Collection<ServerPlayerEntity> sparkwitch$filterForceRoleTargets(
            Collection<ServerPlayerEntity> targetPlayers,
            ServerCommandSource source,
            Collection<ServerPlayerEntity> originalTargetPlayers,
            String roleName
    ) {
        Role role = WitchSkillLockValidationService.findForceableRole(roleName);
        if (role == null || targetPlayers.isEmpty()) {
            return targetPlayers;
        }

        List<ServerPlayerEntity> allowedTargets = new ArrayList<>();
        for (ServerPlayerEntity targetPlayer : targetPlayers) {
            WitchSkillLockValidationService.RoleConflict conflict =
                    WitchSkillLockValidationService.findForcedSkillRoleConflict(targetPlayer, role);
            if (conflict == null) {
                allowedTargets.add(targetPlayer);
                continue;
            }

            source.sendFeedback(() -> Text.literal("Skipped " + targetPlayer.getGameProfile().getName()
                    + ": " + WitchSkillLockValidationService.forceAbilityConflictMessage(conflict)), false);
        }
        return allowedTargets;
    }
}
