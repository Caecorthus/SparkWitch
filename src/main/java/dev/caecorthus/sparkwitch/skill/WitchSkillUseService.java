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

    /**
     * Readiness gate for a role skill that runs its own request/session packets instead of {@link #use}: the active
     * skill must be {@code skillId} and the shared cooldown must have ended. Sends the same action-bar refusal as
     * {@link #use}; role, life and Fear checks stay with the caller.
     * 供使用自有请求/会话数据包（而非 {@link #use}）的职业技能使用的就绪检查：当前技能必须为 {@code skillId}，
     * 且共享冷却已结束。拒绝时发送与 {@link #use} 相同的动作栏提示；职业、存活与恐惧检查由调用方负责。
     */
    public static boolean checkDedicatedSkillReady(ServerPlayerEntity player, Identifier skillId) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier activeSkillId = component.getActiveSkillId();
        if (skillId == null || !skillId.equals(activeSkillId)) {
            send(player, "message.sparkwitch.skill.no_skill");
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        WitchSkillUseReadiness.Result readiness = WitchSkillUseReadiness.check(
                role, component, activeSkillId, WitchSkillRegistry.get(activeSkillId));
        if (!readiness.accepted()) {
            send(player, readiness.messageKey(), readiness.messageArgs());
            if (readiness.clearComponent()) {
                component.clear();
            }
            return false;
        }
        return true;
    }

    /**
     * Starts the registered post-use cooldown for a dedicated-flow skill on the shared skill cooldown, so the
     * bottom-right HUD shows it exactly like a generic use.
     * 在共享技能冷却上为自有流程技能启动注册的使用后冷却，右下角 HUD 的显示与通用使用完全一致。
     */
    public static void startDedicatedSkillCooldown(ServerPlayerEntity player, Identifier skillId) {
        WitchSkillDefinition skill = skillId == null ? null : WitchSkillRegistry.get(skillId);
        if (skill == null) {
            return;
        }
        WitchSkillCooldownPolicy.apply(
                WitchPlayerComponent.KEY.get(player),
                WitchSkillCooldownPolicy.decide(skill, WitchSkillUseResult.success(0)));
    }

    private static void send(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }
}
