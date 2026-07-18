package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintAbilityService;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;


public final class WitchSkillUseService {
    private WitchSkillUseService() {
    }

    public static boolean use(ServerPlayerEntity player, Optional<UUID> targetUuid) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        Role role = gameComponent.getRole(player);
        if (!SparkWitchRoles.isSparkWitchRole(role)) {
            send(player, "message.sparkwitch.skill.not_witch");
            return false;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            send(player, "message.sparkwitch.skill.dead");
            return false;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return false;
        }
        if (SaintRules.isSaint(role)) {
            return SaintAbilityService.use(player);
        }

        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        WitchSkillDefinition skill = skillId == null ? null : WitchSkillRegistry.get(skillId);
        WitchSkillUseReadiness.Result readiness = WitchSkillUseReadiness.check(role, component, skillId, skill);
        if (!readiness.accepted()) {
            send(player, readiness.messageKey(), readiness.messageArgs());
            if (readiness.clearComponent()) {
                component.clear();
            }
            return false;
        }

        ServerPlayerEntity target = null;
        if (targetUuid.isPresent()) {
            if (world.getPlayerByUuid(targetUuid.get()) instanceof ServerPlayerEntity serverTarget) {
                target = serverTarget;
            } else {
                send(player, "message.sparkwitch.skill.unavailable");
                return false;
            }
        }

        WitchSkillUseResult result = skill.use(new WitchSkillUseContext(world, gameComponent, player, role, skill, target));
        if (!result.accepted()) {
            send(player, result.messageKey() == null ? "message.sparkwitch.skill.unavailable" : result.messageKey());
            return false;
        }

        WitchSkillCooldownPolicy.apply(component, WitchSkillCooldownPolicy.decide(skill, result));
        if (result.messageKey() != null) {
            send(player, result.messageKey());
        }
        return true;
    }

    private static void send(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }
}
