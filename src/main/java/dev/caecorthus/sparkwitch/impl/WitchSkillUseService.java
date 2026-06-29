package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

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

        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        if (skillId == null) {
            send(player, "message.sparkwitch.skill.no_skill");
            return false;
        }
        if (component.getCooldownTicks() > 0) {
            send(player, "message.sparkwitch.skill.cooldown", Math.ceil(component.getCooldownTicks() / 20.0));
            return false;
        }
        if (component.hasDeferredCooldown()) {
            send(player, "message.sparkwitch.skill.cooldown", Math.ceil(component.getActiveSkillWindowTicks() / 20.0));
            return false;
        }

        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        if (skill == null) {
            send(player, "message.sparkwitch.skill.unknown");
            component.clear();
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

        int cooldownTicks = Math.max(skill.cooldownTicks(), result.cooldownTicks());
        if (result.deferCooldownUntilActiveWindowEnds()) {
            component.deferCooldownUntilActiveWindowEnds(cooldownTicks);
        } else {
            component.setCooldownTicks(cooldownTicks);
        }
        if (result.messageKey() != null) {
            send(player, result.messageKey());
        }
        return true;
    }

    private static void send(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }
}
