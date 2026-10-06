package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler of {@code sparkwitch:use_fiend_dash} (the NoellesRoles ability key). Dash is a role-owned, moment-only
 * skill: it never enters the witch skill registry, the shared witch-skill cooldowns or the witch skill panel; its
 * cooldown is an absolute ready tick in the {@code sparkwitch:fiend_moment} world component (synced only to the moment
 * Fiend) and dies with the moment. Forced cooldowns from other features reach it only through
 * {@code compat/cooldown/FiendDashCooldownStore}, which only moves the ready tick later.
 * {@code sparkwitch:use_fiend_dash} 的服务端处理（NoellesRoles 技能键）。疾驰是职业自有、仅限时刻中的技能：从不进入魔女
 * 技能注册表、共享的魔女技能冷却或魔女技能面板；其冷却以绝对就绪刻保存在 {@code sparkwitch:fiend_moment} 世界组件中
 * （只同步给时刻中的魔人），并随时刻一同结束。其他功能的强制冷却只经 {@code compat/cooldown/FiendDashCooldownStore}
 * 作用于它，且只会把就绪刻推后。
 */
public final class FiendDashService {
    private FiendDashService() {
    }

    /**
     * Called on the server thread for every Dash request. The stun, Seeker-session, Rift-session and Fear payload
     * guards already drop most refused requests; this re-checks every gate against server state. A refusal costs
     * nothing.
     * 每个疾驰请求都在服务端线程调用。眩晕、搜寻者会话、裂隙会话与恐惧的数据包拦截已丢弃大多数被拒请求；此处按服务端
     * 状态重新检查每个门槛。拒绝时不消耗任何东西。
     */
    public static void tryUse(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        long now = player.getServerWorld().getTime();
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(player.getServerWorld());
        FiendDashRules.DashVerdict verdict = FiendDashRules.verdict(
                FiendParticipation.isMomentFiend(player),
                GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                moment.isActive() && !moment.isComplete(),
                GameFunctions.isPlayerPlayingAndAlive(player),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                ControlExpertStun.isStunned(player),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                moment.isDashReady(),
                GrandWitchFearService.isPlayerFeared(player));
        switch (verdict) {
            case USE -> {
                FiendMomentEffects.grantDash(player);
                moment.setDashReadyTick(now + FiendRules.DASH_COOLDOWN_TICKS);
            }
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case REFUSE -> {
            }
        }
    }
}
