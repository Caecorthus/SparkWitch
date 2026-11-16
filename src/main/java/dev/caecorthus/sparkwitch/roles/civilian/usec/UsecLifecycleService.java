package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * USEC lifecycle (WP2): the round-start and mid-round rifle grant, the terminal cleanups (death, reset, a disconnect
 * that Wathe turns into an ESCAPED death) and the death revolver. Called once from {@link UsecFeatureService#register()}.
 * Server only. Owner, 2026-11-05 ("AXMC 用不了是个 Bug", "只是死后不掉落罢了", and the BEAR M32A1 answer "直接走 wathe
 * 的机制"): any holder keeps and uses the five bound items; Wathe's own rules decide every drop, and nothing strips them
 * from a holder (no sweep, no role-change strip, no world-target veto). They vanish only when the holder's items end:
 * the holder's death (from any holder, not only the USEC), Wathe's reset and a disconnect from a live round.
 * USEC 生命周期（WP2）：开局与中途发枪、终局清理（死亡、重置、被 Wathe 转为 ESCAPED 死亡的断线）与死亡掉落左轮。由
 * {@link UsecFeatureService#register()} 调用一次。仅服务端。所有者 2026-11-05（“AXMC 用不了是个 Bug”“只是死后不掉落罢了”，
 * 以及 BEAR M32A1 的回答“直接走 wathe 的机制”）：任何持有者都能保留并使用五种绑定物品；一切丢弃由 Wathe 自身规则决定，
 * 也没有任何东西会从持有者身上收走它们（没有清扫、没有换职业收走、没有世界目标否决）。它们只在持有者的物品本就结束时消失：
 * 持有者死亡（任何持有者，不只是 USEC）、Wathe 重置，以及在进行中的对局里断线。
 */
public final class UsecLifecycleService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for USEC, ordered after the default phase (the Seeker and Blind pattern), so
     * every default-phase listener has run. Roles are already final here: SparkTraits Conscience compensation rewrites
     * them during role assignment, before the roles are announced. The round is still STARTING.
     * USEC 在 Wathe ON_FINISH_INITIALIZE 上使用的阶段，排在默认阶段之后（与搜寻者、盲人相同），因此所有默认阶段监听器都已
     * 执行。此时身份已是最终身份：SparkTraits 良心补偿在分配职业期间（公布身份之前）就已改写身份。对局仍处于 STARTING。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("usec_finish_initialize");

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
        // Wathe's finalizeGame already reset (and emptied) every player of that world; the end of a round strips nothing
        // else, so a holder elsewhere keeps their copy. / Wathe 的 finalizeGame 已重置（并清空）该世界的所有玩家；回合结束不再
        // 收走其他任何东西，因此身在别处的持有者保留其物品。
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : List.copyOf(serverWorld.getServer().getPlayerManager().getPlayerList())) {
                    UsecLoadoutService.forget(player);
                }
                UsecLoadoutService.forgetRound();
                UsecDeathDrops.forgetAll();
            }
        });
        ServerDisconnectCleanup.register((player, server) ->
                onDisconnect(player, GameFunctions.isPlayerPlayingAndAlive(player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            UsecLoadoutService.forgetRound();
            UsecDeathDrops.forgetAll();
        });
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
     * Every RoleAssigned. Becoming USEC while the round is ACTIVE grants once (a dead or spectating player gets nothing);
     * round-start assignments (STARTING) wait for the final-role phase. Losing USEC changes nothing: the former USEC
     * keeps and may use the rifle and its parts (owner, 2026-11-05), and the grant record stays for the round.
     * 每次 RoleAssigned。在 ACTIVE 对局中成为 USEC 时发放一次（死亡或旁观的玩家不获得任何东西）；开局分配（STARTING）等待
     * 最终身份阶段。失去 USEC 不做任何改变：原 USEC 保留并可继续使用步枪及其配件（所有者 2026-11-05），本回合的发放记录也保留。
     */
    static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        if (!UsecRules.isUsec(role)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (UsecLoadoutService.receivesMidRoundGrant(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), UsecLoadoutService.isGranted(player))) {
            UsecLoadoutService.grant(player);
        }
    }

    /**
     * A disconnect from a live round is a death: Wathe schedules the ESCAPED kill (which still drops the USEC's
     * revolver through {@code KillPlayer.AFTER}) only after the player has left and been saved, so the bound items are
     * stripped here, before the save. Every other holder keeps them across the relog.
     * 在进行中的对局里断线即死亡：Wathe 安排的 ESCAPED 击杀（仍会经 {@code KillPlayer.AFTER} 掉落 USEC 的左轮）发生在玩家离开并
     * 保存之后，因此绑定物品在此处、保存之前清除。其他持有者重新登录后仍保留它们。
     *
     * @param livingParticipant Wathe {@code isPlayerPlayingAndAlive}, the same test as Wathe's own ESCAPED kill
     */
    static void onDisconnect(ServerPlayerEntity player, boolean livingParticipant) {
        if (stripsOnDisconnect(livingParticipant)) {
            cleanUp(player);
            return;
        }
        UsecLoadoutService.forget(player);
        UsecDeathDrops.forget(player);
    }

    /** Pure: only a disconnect Wathe turns into a death strips. / 纯函数：只有被 Wathe 转为死亡的断线才清除。 */
    static boolean stripsOnDisconnect(boolean livingParticipant) {
        return livingParticipant;
    }

    private static void cleanUp(ServerPlayerEntity player) {
        UsecLoadoutService.cleanUp(player);
        UsecDeathDrops.forget(player);
    }
}
