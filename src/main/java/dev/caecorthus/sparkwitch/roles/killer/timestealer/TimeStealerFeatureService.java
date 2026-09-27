package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Registers every Time Stealer lifecycle hook and dispatches to the role's services, keeping the global event owner
 * declarative. Gameplay rules live in the called services; this class only decides which hook reaches which service.
 * 注册窃时者的全部生命周期钩子并分发给各职业服务，保持全局事件聚合器仅做声明式注册。玩法规则位于被调用的服务中；
 * 本类只决定哪个钩子分发到哪个服务。
 */
public final class TimeStealerFeatureService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for the match binding, ordered after the default phase so Wathe's own
     * listener has already started the replay match (the id bound here), as the Seeker does.
     * 对局绑定使用的 Wathe ON_FINISH_INITIALIZE 阶段，排在默认阶段之后，确保 Wathe 自身的监听器已开始回放对局
     * （此处绑定的 id），与搜寻者相同。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("time_stealer_finish_initialize");

    private static boolean registered;

    private TimeStealerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TimeStealerShopService.register();
        TimeStealerReplay.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            // Curses cast by this player keep running whatever the new role is (owner decision Q7); only the
            // Clock, stamps, and cooldown follow the final role.
            // 无论新职业为何，该玩家施下的诅咒都会继续结算（所有者决定 Q7）；只有时钟、邮票与冷却跟随最终职业。
            TimeStealerLoadoutService.onRoleAssigned(serverPlayer, role);
            TimeStealerStampService.onRoleAssigned(serverPlayer, role);
        });
        // Wathe starts the match record inside its own default-phase ON_FINISH_INITIALIZE listener, so the id is bound
        // in a later phase; the owner tick re-binds a holder who missed this (TimeStealerStampService.tickOwner).
        // Wathe 在自身默认阶段的 ON_FINISH_INITIALIZE 监听器中才开始对局记录，因此在更晚的阶段绑定对局 id；
        // 错过此处的持有者由持有者 tick 重新绑定（TimeStealerStampService.tickOwner）。
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            TimeTheftRuntime.forgetTimekeeperPurge();
            UUID matchId = TimeStealerMatch.currentId();
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                TimeTheftPlayerComponent theft = TimeTheftPlayerComponent.KEY.get(player);
                if (theft.isStolen() && !Objects.equals(theft.matchId(), matchId)) {
                    TimeTheftRuntime.reset(player);
                    theft.clear();
                }
                TimeStealerPlayerComponent.KEY.get(player).bindMatch(matchId);
            }
        });
        // Only the dead player's own curse and bound items are dropped; curses this player cast keep settling
        // (owner decision Q7). A SparkTraits-intercepted death leaves the player in play, so nothing is cleared.
        // 只清除死者自身的诅咒与绑定物品；该玩家施下的诅咒继续结算（所有者决定 Q7）。
        // 被 SparkTraits 拦截的死亡使玩家仍留在对局中，因此不清除任何内容。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            TimeTheftRuntime.onDeath(victim);
            TimeStealerLoadoutService.onDeath(victim);
        });
        ResetPlayer.EVENT.register(TimeStealerFeatureService::clearPlayer);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            TimeTheftRuntime.forgetTimekeeperPurge();
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                clearPlayer(player);
            }
        });
        // AFTER fires only for a committed purchase; the counter catches its own exceptions (tryBuy does not).
        // AFTER 只在购买成功后触发；计数器自行捕获异常（tryBuy 不会捕获）。
        ShopPurchase.AFTER.register(TimekeeperCounter::onPurchase);
        // Second owner-approved exception: like the bell toll, the Clock curse kill is SparkTraits-terminal. SparkTraits
        // reads terminal reasons from a live set at kill time, so registering on server start is early enough and
        // idempotent across restarts; an absent or older build registers nothing (fail-closed).
        // 第二个所有者批准的例外：与丧钟相同，时钟诅咒击杀注册为 SparkTraits 终结原因。SparkTraits 在击杀时实时读取
        // 终结原因集合，因此服务端启动时注册即足够早，且多次启动重复注册是幂等的；缺失或旧版本不注册（失败关闭）。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.TIME_STOLEN));
    }

    private static void clearPlayer(ServerPlayerEntity player) {
        TimeTheftRuntime.reset(player);
        TimeTheftPlayerComponent.KEY.get(player).clear();
        TimeStealerLoadoutService.reset(player);
        TimeStealerStampService.reset(player);
        TimeStealerPlayerComponent.KEY.get(player).clear();
    }
}
