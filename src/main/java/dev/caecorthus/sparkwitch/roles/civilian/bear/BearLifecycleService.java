package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BEAR lifecycle: the round-start and mid-round launcher grant and the terminal cleanups (the AXMC #199 model, owner
 * 2026-11-05 "直接走 wathe 的机制"). Server only. Every holder who dies loses all BEAR items ({@code KillPlayer.AFTER}
 * deletes them; {@code mixin.bear.GameFunctionsBearItemDropMixin} keeps them out of Wathe's death-drop loop and
 * {@code mixin.bear.PlayerEntityBearItemMixin} out of vanilla's), so nothing BEAR ever drops. A SparkTraits-intercepted
 * death (Last Stand) keeps everything. Wathe's reset also strips the cursor and own screen slots (it clears the
 * inventory itself; {@code HeadsetLifecycle} clears the headset slot); a disconnect from a live round strips before
 * the save, since Wathe's ESCAPED kill only runs afterwards. Role changes and the round end strip nothing.
 * BEAR 生命周期：开局与中途发放发射器，以及终局清理（沿用 AXMC #199 模式，所有者 2026-11-05“直接走 wathe 的机制”）。仅服务端。
 * 任何持有者死亡都会失去所有 BEAR 物品（{@code KillPlayer.AFTER} 删除它们；{@code mixin.bear.GameFunctionsBearItemDropMixin}
 * 使其不进入 Wathe 死亡掉落流程，{@code mixin.bear.PlayerEntityBearItemMixin} 使其不进入原版掉落），因此 BEAR 物品从不掉落。被
 * SparkTraits 拦截的死亡（背水一战）保留一切。Wathe 重置时还会清除光标与自身界面栏位（背包由 Wathe 自己清空；耳机槽由
 * {@code HeadsetLifecycle} 清空）；在进行中的对局里断线会在存档前清除，因为 Wathe 的 ESCAPED 击杀在之后才执行。换职业与回合
 * 结束都不收走任何东西。
 */
final class BearLifecycleService {
    /** BEAR's ON_FINISH_INITIALIZE phase, after the default one (roles are final). / BEAR 的 ON_FINISH_INITIALIZE 阶段。 */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("bear_finish_initialize");

    private static boolean registered;

    private BearLifecycleService() {
    }

    static synchronized void register() {
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
                afterKill(victim);
            }
        });
        ResetPlayer.EVENT.register(BearLoadoutService::cleanUp);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : List.copyOf(serverWorld.getServer().getPlayerManager().getPlayerList())) {
                    BearLoadoutService.forget(player);
                }
                BearLoadoutService.forgetRound();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                onDisconnect(handler.player, GameFunctions.isPlayerPlayingAndAlive(handler.player));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BearLoadoutService.forgetRound());
    }

    /** Every final, living BEAR gets exactly one grant. / 每名最终存活的 BEAR 恰好获得一次发放。 */
    static void onFinishInitialize(ServerWorld world, GameWorldComponent game) {
        BearLoadoutService.forgetRound();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (BearLoadoutService.receivesRoundStartGrant(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                BearLoadoutService.grant(player);
            }
        }
    }

    /**
     * Becoming BEAR while ACTIVE grants once; STARTING assignments wait for the final-role phase; losing BEAR changes
     * nothing. / 在 ACTIVE 对局中成为 BEAR 时发放一次；STARTING 阶段的分配等待最终职业阶段；失去 BEAR 不做任何改变。
     */
    static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        if (!BearRules.isBear(role)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (BearLoadoutService.receivesMidRoundGrant(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), BearLoadoutService.isGranted(player))) {
            BearLoadoutService.grant(player);
        }
    }

    /** {@code KillPlayer.AFTER} for every victim. / 对每名受害者执行的 {@code KillPlayer.AFTER}。 */
    static void afterKill(ServerPlayerEntity victim) {
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        BearLoadoutService.cleanUp(victim);
    }

    /**
     * @param livingParticipant Wathe {@code isPlayerPlayingAndAlive}, the same test as Wathe's own ESCAPED kill
     */
    static void onDisconnect(ServerPlayerEntity player, boolean livingParticipant) {
        if (stripsOnDisconnect(livingParticipant)) {
            BearLoadoutService.cleanUp(player);
            return;
        }
        BearLoadoutService.forget(player);
    }

    /** Pure: only a disconnect Wathe turns into a death strips. / 纯函数：只有被 Wathe 转为死亡的断线才清除。 */
    static boolean stripsOnDisconnect(boolean livingParticipant) {
        return livingParticipant;
    }
}
