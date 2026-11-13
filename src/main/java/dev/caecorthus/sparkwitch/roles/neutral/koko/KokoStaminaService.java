package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Runtime glue for {@code mixin.koko.KokoSprintDrainMixin}: Koko's sprint drains half as fast while she is live, on
 * Wathe's normal cap and regeneration (the Discoverer's rewrite with her gate and multiplier). Client/server
 * authority: the server copy is the authority and syncs through Wathe's {@code PlayerStaminaComponent}; her own client
 * applies the same rule to its prediction, reading the stream from the owner-synced {@link KokoComponent}, so it
 * neither empties the bar early nor stops the sprint before the server does. The client never sends stamina.
 * {@code mixin.koko.KokoSprintDrainMixin} 的运行时接线：可可直播期间疾跑消耗减半，Wathe 的普通上限与恢复速度不变（探索者的
 * 改写方式，换成她的门槛与倍率）。客户端/服务端权威：服务端副本是权威，经 Wathe 的 {@code PlayerStaminaComponent} 同步；她
 * 自己的客户端对预测施加同样的规则，直播状态取自只同步给本人的 {@link KokoComponent}，因此既不会提前耗尽体力条，也不会先于
 * 服务端停止疾跑。客户端从不发送体力。
 */
public final class KokoStaminaService {
    private KokoStaminaService() {
    }

    /**
     * Stamina before Wathe's tick, or {@link KokoRules#NOT_CAPTURED} for a player this side does not track.
     * Wathe 体力刻之前的体力值；本端不跟踪的玩家返回 {@link KokoRules#NOT_CAPTURED}。
     */
    public static float captureSprintingTicks(PlayerEntity player) {
        if (!KokoRules.tracksStaminaOnThisSide(player instanceof ServerPlayerEntity, player.isMainPlayer())) {
            return KokoRules.NOT_CAPTURED;
        }
        return PlayerStaminaComponent.KEY.get(player).getSprintingTicks();
    }

    /** Cap before Wathe's tick (Wathe clamps only when it shrank). / Wathe 体力刻之前的上限（仅在缩小时截断）。 */
    public static int captureMaxSprintTime(PlayerEntity player) {
        return PlayerStaminaComponent.KEY.get(player).getMaxSprintTime();
    }

    /**
     * Runs right after Wathe's stamina tick. A tick still sprinting is exactly Wathe's drain branch, so only that
     * tick's drain is rewritten; regeneration, the cap, exhaustion, jump costs and trait bonuses stay with their
     * owners. No sync call: Wathe's component tick syncs on its own thresholds.
     * 紧接在 Wathe 体力刻之后执行。仍在疾跑的刻恰好对应 Wathe 的消耗分支，因此只改写该刻的消耗；恢复、上限、疲惫、跳跃消耗
     * 与词条加成仍归各自所有者。不主动同步：Wathe 组件刻会按自身阈值同步。
     */
    public static void halveSprintDrain(PlayerEntity player, float previous, int previousMax) {
        if (Float.isNaN(previous) || !player.isSprinting()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game == null) {
            return;
        }
        // Runs for every sprinting player every tick: the role test rejects everyone else before the other lookups.
        // 每 tick 对每名疾跑玩家执行：先用职业判断排除其他人，再做其余查询。
        Role role = game.getRole(player);
        if (!KokoRules.isKoko(role) || !KokoRules.halvesSprintDrain(game.isRunning(),
                GameFunctions.isPlayerAliveAndSurvival(player), role, KokoComponent.KEY.get(player).isLive())) {
            return;
        }
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        if (stamina.isInfiniteStamina()) {
            return;
        }
        float current = stamina.getSprintingTicks();
        float halved = KokoRules.halvedSprintStamina(previous, previousMax, stamina.getMaxSprintTime(), current);
        if (halved != current) {
            stamina.setSprintingTicks(halved);
        }
    }
}
