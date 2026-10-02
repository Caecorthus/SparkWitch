package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler of {@code sparkwitch:use_blind_attune} (G key). Attune is a role-owned skill: it never enters the
 * witch skill registry, the shared witch-skill cooldowns or the witch skill panel; its window and cooldown live in the
 * owner-only {@code sparkwitch:blind} component, and perception reads the window from there.
 * {@code sparkwitch:use_blind_attune} 的服务端处理（G 键）。凝神是职业自有技能：从不进入魔女技能注册表、共享的魔女
 * 技能冷却或魔女技能面板；其窗口与冷却保存在仅本人同步的 {@code sparkwitch:blind} 组件中，感知从那里读取窗口。
 */
public final class BlindAttuneService {
    private BlindAttuneService() {
    }

    /**
     * Called on the server thread for every Attune request. The stun, Seeker-session and Fear payload guards already
     * drop most refused requests; this re-checks every gate against server state. A refusal costs nothing.
     * 每个凝神请求都在服务端线程调用。眩晕、搜寻者会话与恐惧的数据包拦截已丢弃大多数被拒请求；此处按服务端状态
     * 重新检查每个门槛。拒绝时不消耗任何东西。
     */
    public static void tryUse(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        long now = player.getServerWorld().getTime();
        BlindComponent state = BlindComponent.KEY.get(player);
        boolean activeBlind = BlindParticipants.isActiveBlind(player);
        BlindKitRules.AttuneVerdict verdict = BlindKitRules.attuneVerdict(
                activeBlind,
                GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                activeBlind && BlindLoadoutService.isGranted(player),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                ControlExpertStun.isStunned(player),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                state.attuneReady(now),
                GrandWitchFearService.isPlayerFeared(player));
        switch (verdict) {
            case USE -> {
                BlindKitRules.Window window = BlindKitRules.attuneUse(now);
                state.setAttune(window.activeUntilTick(), window.readyTick());
            }
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case REFUSE -> {
            }
        }
    }
}
