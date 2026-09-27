package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Seeker lifecycle hooks (game start sweep, finish-initialize binding and loadout, role change, final death, reset,
 * win, finalize sweep, disconnect, server stop). Server only. The Taotie victim's {@code KillPlayer.AFTER} return is
 * WP-06's own listener; this service never touches NoellesRoles.
 * 搜寻者生命周期钩子（开局清扫、初始化完成时的绑定与装备、职业变更、最终死亡、重置、胜负判定、结算清扫、断线、
 * 服务器停止）。仅服务端。饕餮受害者的 {@code KillPlayer.AFTER} 归还由 WP-06 自己的监听器负责；本服务从不触及 NoellesRoles。
 *
 * <p>Final death clears state only through a non-intercepted {@code KillPlayer.AFTER} or the component's tick fallback
 * ({@code isPlayerDead && !isLastStandPending}); alive-spectator states (swallowed body, Last Stand) only end the
 * session, so devices, LostTo and PendingReturn survive a release or revive.
 * 最终死亡只通过未被拦截的 {@code KillPlayer.AFTER} 或组件刻兜底清空状态；活着的旁观状态（本体被吞、背水一战）
 * 只结束会话，因此设备、LostTo 与 PendingReturn 在释放或复活后依然保留。
 */
public final class SeekerLifecycleService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for the Seeker, ordered after the default phase so Wathe has started the
     * replay match (the binding id) and every default listener (e.g. SparkTraits compensation) has settled final roles.
     * 搜寻者在 Wathe ON_FINISH_INITIALIZE 上使用的阶段，排在默认阶段之后：此时 Wathe 已开始回放对局（绑定 id），
     * 所有默认监听器（如 SparkTraits 补偿）也已确定最终身份。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("seeker_finish_initialize");

    private static boolean registered;
    private static volatile @Nullable MinecraftServer server;

    private SeekerLifecycleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);

        // Stray devices from an aborted round never enter the new one. / 中止回合残留的设备不会进入新回合。
        GameEvents.ON_GAME_START.register(gameMode -> {
            MinecraftServer current = server;
            if (current != null) {
                SeekerDeviceService.sweepAll(current);
            }
        });

        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                onFinishInitialize(serverWorld, game);
            }
        });

        // Any role change away from Seeker drops the session, devices and state; recruitment removes items only.
        // 任何从搜寻者转出的职业变更都会清除会话、设备与状态；招募只移除物品，不移除实体。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && !SeekerRules.isSeeker(role)) {
                cleanUp(serverPlayer, SeekerExitReason.ROLE_CHANGED);
            }
        });

        // Last Stand gate: an intercepted death keeps devices; the component tick ends the session and later decides.
        // 背水一战门槛：被拦截的死亡保留设备；由组件刻结束会话并在之后判定。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim == null || !holdsState(victim) || WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            cleanUp(victim, SeekerExitReason.DIED);
        });

        ResetPlayer.EVENT.register(player -> cleanUp(player, SeekerExitReason.ROUND_END));

        // Nobody watches the end screen from a car. / 不让任何人从小车视角观看结算画面。
        GameEvents.ON_WIN_DETERMINED.register((world, game, winStatus, neutralWinner) -> {
            for (ServerPlayerEntity player : List.copyOf(world.getServer().getPlayerManager().getPlayerList())) {
                endSession(player, SeekerExitReason.ROUND_END);
            }
        });

        // Wathe never removes custom entities: sweep every world, then clear every component.
        // Wathe 从不移除自定义实体：清扫所有世界，再清空所有组件。
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                MinecraftServer current = serverWorld.getServer();
                SeekerDeviceService.sweepAll(current);
                for (ServerPlayerEntity player : List.copyOf(current.getPlayerManager().getPlayerList())) {
                    cleanUp(player, SeekerExitReason.ROUND_END);
                }
            }
        });

        // Only the session ends here; Wathe's ESCAPED kill then runs the KillPlayer.AFTER cleanup.
        // 这里只结束会话；随后 Wathe 的 ESCAPED 击杀会执行 KillPlayer.AFTER 清理。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, disconnected) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                endSession(player, SeekerExitReason.DISCONNECTED);
                SeekerStatusComponent.KEY.get(player).setSessionState(null);
            }
        });
    }

    /**
     * Still STARTING here, so no running-state helper is used. Every online player is bound to the new match (state
     * from another match is dropped first); each final, living Seeker gets exactly one loadout grant.
     * 此时对局仍处于 STARTING，因此不使用运行态判定。所有在线玩家绑定到新对局（先清除属于其他对局的状态）；
     * 每名最终存活的搜寻者恰好获得一次开局装备。
     */
    static void onFinishInitialize(ServerWorld world, GameWorldComponent game) {
        String matchId = SeekerTargeting.currentMatchId(world);
        if (matchId == null) {
            SparkWitch.LOGGER.warn("Seeker could not bind the round: Wathe reported no active match");
        }
        for (ServerPlayerEntity player : List.copyOf(world.getServer().getPlayerManager().getPlayerList())) {
            SeekerStatusComponent component = SeekerStatusComponent.KEY.get(player);
            if (!Objects.equals(component.state().matchId(), matchId)) {
                cleanUp(player, SeekerExitReason.ROUND_END);
            }
            if (matchId != null) {
                component.bindMatch(matchId);
            }
        }
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (receivesLoadout(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))) {
                SeekerLoadoutService.grant(player);
            }
        }
    }

    /** Final roles only; a mid-round Seeker never receives the loadout. / 仅按最终身份；对局中途的搜寻者不获得装备。 */
    static boolean receivesLoadout(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && SeekerRules.isSeeker(finalRole);
    }

    /**
     * Terminal cleanup for one player, in order: end the session (WP-09), discard both devices (WP-03), clear the
     * component. A no-op for players holding no Seeker state. Also the component tick's self-heal and final-death path.
     * 单名玩家的终局清理，顺序：结束会话（WP-09）、移除两个设备（WP-03）、清空组件。对不持有搜寻者状态的玩家为空操作。
     * 同时也是组件刻自愈与最终死亡路径。
     */
    static void cleanUp(ServerPlayerEntity player, SeekerExitReason reason) {
        if (!holdsState(player)) {
            return;
        }
        SeekerStatusComponent component = SeekerStatusComponent.KEY.get(player);
        SeekerRemoteSessionService.end(player, reason);
        MinecraftServer current = player.getServer();
        if (current != null) {
            SeekerDeviceService.discardAllFor(current, player.getUuid());
        }
        component.clearAll();
    }

    private static boolean holdsState(ServerPlayerEntity player) {
        SeekerStatusComponent component = SeekerStatusComponent.KEY.get(player);
        return !component.state().isIdle() || component.sessionState() != null;
    }

    private static void endSession(ServerPlayerEntity player, SeekerExitReason reason) {
        SeekerStatusComponent component = SeekerStatusComponent.KEY.get(player);
        if (component.sessionMode() != SeekerSessionMode.NONE || component.sessionState() != null) {
            SeekerRemoteSessionService.end(player, reason);
        }
    }
}
