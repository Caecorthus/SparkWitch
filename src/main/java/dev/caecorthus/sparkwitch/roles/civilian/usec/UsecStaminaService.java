package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Runtime glue for {@code mixin.usec.UsecStaminaRegenMixin}. Client/server authority: the server copy is the
 * authority and syncs through Wathe's {@code PlayerStaminaComponent}; the local client applies the same boost to its
 * own prediction, exactly like Wathe applies its base regen on both sides, so the HUD bar fills at the server's rate
 * instead of jumping on every resync. The client value never reaches the server (it sends no stamina), so a wrong
 * prediction is overwritten by the next sync and can never grant stamina. SparkTraits Excellent Physique's bonus is
 * server-only, so with that trait the client still under-predicts and resyncs, as it already does today.
 * {@code mixin.usec.UsecStaminaRegenMixin} 的运行时接线。客户端/服务端权威：服务端副本是权威，经 Wathe 的
 * {@code PlayerStaminaComponent} 同步；本地客户端对自己的预测施加同样的加成，与 Wathe 在两端都执行基础恢复的做法一致，
 * 使 HUD 体力条以服务端速率增长，而不是每次重新同步时跳变。客户端数值从不发往服务端（不发送体力），因此错误预测会被下次
 * 同步覆盖，绝不会凭空获得体力。SparkTraits 体质优异的加成只在服务端执行，带该词条时客户端仍会少算并重新同步，与现状相同。
 */
public final class UsecStaminaService {
    private UsecStaminaService() {
    }

    /**
     * Sprinting ticks before Wathe's stamina tick, or {@link UsecStaminaRules#NOT_CAPTURED} for a player this side
     * does not track (other players on a client).
     * Wathe 体力刻之前的体力值；本端不跟踪的玩家（客户端上的其他玩家）返回 {@link UsecStaminaRules#NOT_CAPTURED}。
     */
    public static float capture(PlayerEntity player) {
        if (!UsecStaminaRules.tracksOnThisSide(player instanceof ServerPlayerEntity, player.isMainPlayer())) {
            return UsecStaminaRules.NOT_CAPTURED;
        }
        return PlayerStaminaComponent.KEY.get(player).getSprintingTicks();
    }

    /**
     * Doubles this tick's regeneration for a running, living, survival USEC. No sync call: Wathe's component tick
     * syncs on its own thresholds.
     * 为对局中存活、生存模式的 USEC 将本刻恢复量翻倍。不主动同步：Wathe 组件刻会按自身阈值同步。
     */
    public static void boostRegen(PlayerEntity player, float previous) {
        if (Float.isNaN(previous)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game == null || !UsecStaminaRules.appliesTo(game.isRunning(),
                GameFunctions.isPlayerAliveAndSurvival(player), game.getRole(player))) {
            return;
        }
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        float current = stamina.getSprintingTicks();
        int maxSprintTime = stamina.getMaxSprintTime();
        if (!UsecStaminaRules.regenerates(player.isSprinting(), stamina.isInfiniteStamina(), maxSprintTime, previous,
                current)) {
            return;
        }
        stamina.setSprintingTicks(UsecStaminaRules.boostedStamina(previous, current, maxSprintTime));
    }
}
