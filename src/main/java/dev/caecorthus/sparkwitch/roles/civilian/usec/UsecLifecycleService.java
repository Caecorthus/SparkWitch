package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * USEC lifecycle (WP2): the round-start and mid-round rifle grant, bound-item cleanup (role change away, terminal
 * death, reset, finalize, disconnect), the death revolver, the world-target veto and the staggered sweep. Called once
 * from {@link UsecFeatureService#register()}. Server-authoritative except the {@code UseEntityCallback} veto, which runs
 * on both sides (the client stops before sending, the server refuses a forged packet). The sweep binds only match
 * participants ({@link OffMatchUse#isMatchParticipant}) and never re-grants.
 * USEC 生命周期（WP2）：开局与中途发枪、绑定物品清理（转出 USEC、最终死亡、重置、收尾、断线）、死亡掉落左轮、世界目标否决与
 * 错峰清扫。由 {@link UsecFeatureService#register()} 调用一次。除 {@code UseEntityCallback} 否决在双端执行（客户端在发包前
 * 拦截，服务端拒绝伪造的数据包）外，均由服务端裁定。清扫只约束对局参与者（{@link OffMatchUse#isMatchParticipant}），且从不补发。
 */
public final class UsecLifecycleService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for USEC, ordered after the default phase (the Seeker and Blind pattern) so
     * every default listener (SparkTraits Conscience compensation) has settled final roles. The round is still STARTING.
     * USEC 在 Wathe ON_FINISH_INITIALIZE 上使用的阶段，排在默认阶段之后（与搜寻者、盲人相同），此时所有默认监听器
     * （SparkTraits 良心补偿）都已确定最终身份。对局仍处于 STARTING。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("usec_finish_initialize");
    /** Cadence of the staggered bound-item sweep. / 绑定物品错峰清扫的间隔。 */
    static final int SWEEP_INTERVAL_TICKS = 20;

    private static boolean registered;

    private UsecLifecycleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                onFinishInitialize(serverWorld, game);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                onRoleAssigned(serverPlayer, role);
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim != null) {
                UsecDeathDrops.afterKill(victim);
            }
        });
        ResetPlayer.EVENT.register(UsecLifecycleService::cleanUp);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : List.copyOf(serverWorld.getServer().getPlayerManager().getPlayerList())) {
                    cleanUp(player);
                }
                UsecLoadoutService.forgetRound();
                UsecDeathDrops.forgetAll();
            }
        });
        // The ESCAPED kill Wathe schedules after this still drops the revolver (KillPlayer.AFTER).
        // Wathe 随后安排的 ESCAPED 击杀仍会掉落左轮（KillPlayer.AFTER）。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                cleanUp(handler.player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            UsecLoadoutService.forgetRound();
            UsecDeathDrops.forgetAll();
        });
        // Item frames, armor stands and allays never take a bound item; decorated pots are DecoratedPotBlockUsecItemMixin.
        // 物品展示框、盔甲架与悦灵绝不收走绑定物品；饰纹陶罐由 DecoratedPotBlockUsecItemMixin 处理。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                UsecInventoryRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        ServerTickEvents.END_WORLD_TICK.register(UsecLifecycleService::sweep);
    }

    /**
     * Every final, living USEC gets exactly one grant; the previous round's grants are forgotten first.
     * 每名最终存活的 USEC 恰好获得一次发放；先忘记上一回合的发放记录。
     */
    static void onFinishInitialize(ServerWorld world, GameWorldComponent game) {
        UsecLoadoutService.forgetRound();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (UsecLoadoutService.receivesRoundStartGrant(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                UsecLoadoutService.grant(player);
            }
        }
    }

    /**
     * Every RoleAssigned. Losing USEC cleans up at once; becoming USEC while the round is ACTIVE grants once (a dead or
     * spectating player gets nothing). Round-start assignments (STARTING) wait for the final-role phase.
     * 每次 RoleAssigned。失去 USEC 立即清理；在 ACTIVE 对局中成为 USEC 时发放一次（死亡或旁观的玩家不获得任何东西）。
     * 开局分配（STARTING）等待最终身份阶段。
     */
    static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        if (!UsecRules.isUsec(role)) {
            UsecLoadoutService.cleanUp(player);
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (UsecLoadoutService.receivesMidRoundGrant(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), UsecLoadoutService.isGranted(player))) {
            UsecLoadoutService.grant(player);
        }
    }

    /** Each player is visited once per interval, offset by entity id. / 每名玩家每个间隔按实体 id 错开访问一次。 */
    static boolean isSweepTick(long worldTime, int entityId) {
        return Math.floorMod(worldTime + entityId, SWEEP_INTERVAL_TICKS) == 0;
    }

    private static void sweep(ServerWorld world) {
        long time = world.getTime();
        GameWorldComponent game = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isSweepTick(time, player.getId())) {
                continue;
            }
            if (game == null) {
                game = GameWorldComponent.KEY.get(world);
            }
            if (UsecLoadoutService.sweepStrips(OffMatchUse.isMatchParticipant(player),
                    UsecRules.isUsec(game.getRole(player)))) {
                UsecLoadoutService.stripAll(player);
            }
        }
    }

    private static void cleanUp(ServerPlayerEntity player) {
        UsecLoadoutService.cleanUp(player);
        UsecDeathDrops.forget(player);
    }
}
