package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Runtime glue for {@code mixin.discoverer.DiscovererSprintDrainMixin}: a 25% slower sprint drain on Wathe's normal
 * cap and regeneration. Client/server authority (the USEC stamina pattern): the server copy is the authority and syncs
 * through Wathe's {@code PlayerStaminaComponent}; the local client applies the same rule to its own prediction, as
 * Wathe runs its stamina tick on both sides, so the client neither empties the bar early nor stops the sprint before
 * the server does. The client never sends stamina, so a wrong prediction is overwritten by the next sync.
 * {@code mixin.discoverer.DiscovererSprintDrainMixin} 的运行时接线：在 Wathe 普通上限与恢复速度不变的前提下，疾跑消耗慢
 * 25%。客户端/服务端权威（沿用 USEC 体力写法）：服务端副本是权威，经 Wathe 的 {@code PlayerStaminaComponent} 同步；
 * 本地客户端对自己的预测施加同样的规则（Wathe 在两端都执行体力刻），因此客户端既不会提前耗尽体力条，也不会先于服务端
 * 停止疾跑。客户端从不发送体力，错误预测会被下次同步覆盖。
 */
public final class DiscovererStaminaService {
    private DiscovererStaminaService() {
    }

    /**
     * Stamina before Wathe's tick, or {@link DiscovererRules#NOT_CAPTURED} for a player this side does not track.
     * Wathe 体力刻之前的体力值；本端不跟踪的玩家返回 {@link DiscovererRules#NOT_CAPTURED}。
     */
    public static float captureSprintingTicks(PlayerEntity player) {
        if (!DiscovererRules.tracksStaminaOnThisSide(player instanceof ServerPlayerEntity, player.isMainPlayer())) {
            return DiscovererRules.NOT_CAPTURED;
        }
        return PlayerStaminaComponent.KEY.get(player).getSprintingTicks();
    }

    /** Cap before Wathe's tick (Wathe clamps only when it shrank). / Wathe 体力刻之前的上限（仅在缩小时截断）。 */
    public static int captureMaxSprintTime(PlayerEntity player) {
        return PlayerStaminaComponent.KEY.get(player).getMaxSprintTime();
    }

    /**
     * Runs right after Wathe's stamina tick. A tick still sprinting is exactly Wathe's drain branch (Wathe has already
     * stopped exhausted, empty or depressed sprinters), so only that tick's drain is rewritten; regeneration, the cap,
     * exhaustion, jump costs and trait bonuses are left to their owners. No sync call: Wathe's component tick syncs on
     * its own thresholds.
     * 紧接在 Wathe 体力刻之后执行。仍在疾跑的刻恰好对应 Wathe 的消耗分支（疲惫、体力耗尽或抑郁的疾跑已被 Wathe 停止），
     * 因此只改写该刻的消耗；恢复、上限、疲惫、跳跃消耗与词条加成仍归各自所有者。不主动同步：Wathe 组件刻会按自身阈值同步。
     */
    public static void slowSprintDrain(PlayerEntity player, float previous, int previousMax) {
        if (Float.isNaN(previous) || !player.isSprinting()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game == null) {
            return;
        }
        // Runs for every sprinting player every tick: the role test rejects non-Discoverers before the other (side-effect
        // free) lookups. / 每 tick 对每名疾跑玩家执行：先用职业判断排除非探索者，再做其余（无副作用的）查询。
        Role role = game.getRole(player);
        if (!DiscovererRules.isDiscoverer(role) || !DiscovererRules.slowsSprintDrain(game.isRunning(),
                GameFunctions.isPlayerAliveAndSurvival(player), role)) {
            return;
        }
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        if (stamina.isInfiniteStamina()) {
            return;
        }
        float current = stamina.getSprintingTicks();
        float slowed = DiscovererRules.slowedSprintStamina(previous, previousMax, stamina.getMaxSprintTime(), current);
        if (slowed != current) {
            stamina.setSprintingTicks(slowed);
        }
    }
}
