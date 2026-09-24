package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Registers Bell Ringer lifecycle hooks while keeping the global event owner declarative.
 * 注册敲钟人生命周期钩子，同时保持全局事件聚合器仅做声明式注册。
 */
public final class BellRingerFeatureService {
    private static boolean registered;

    private BellRingerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BellRingerShopService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            // Owner decision: any mid-round role change drops the Echo (marker and its task) and hint silently,
            // no penalty; the task goes too so the new role cannot cash it in as an ordinary task.
            // 所有者决定：对局中任何职业变更都静默清除回响（标记及其任务）与提示，不施加惩罚；
            // 任务一并移除，避免新职业将其当作普通任务完成领取奖励。
            BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.get(serverPlayer);
            BellRingerEchoRuntime.dropEchoSilently(serverPlayer, component);
            component.clearHeard();
            if (!BellRingerRules.isBellRinger(role)) {
                component.setTollReady(false);
            }
            BellRingerLoadoutService.assignForRole(serverPlayer, role);
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            UUID matchId = BellRingerMatch.currentId();
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.get(player);
                if (!Objects.equals(component.matchId(), matchId)) {
                    component.clear();
                }
                component.bindMatch(matchId);
            }
        });
        // Issued Echo tasks keep resolving after the ringer dies; only the victim's own state is dropped.
        // 敲钟人死亡后已发出的回响任务继续结算；只清除死者自身的状态。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            BellRingerEchoRuntime.clearPlayer(victim);
            BellRingerLoadoutService.removeBells(victim);
        });
        ResetPlayer.EVENT.register(BellRingerFeatureService::clearPlayer);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                clearPlayer(player);
            }
        });
        // SparkTraits keeps terminal reasons in a live set read at kill time, so registering once the server
        // starts (every mod initialised, on the server thread) is early enough and idempotent across restarts.
        // SparkTraits 在击杀时实时读取终结原因集合；在服务端启动时（所有模组已初始化、位于服务端线程）
        // 注册即足够早，且多次启动重复注册是幂等的。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.BELL_TOLL));
    }

    private static void clearPlayer(ServerPlayerEntity player) {
        BellEchoPlayerComponent.KEY.get(player).clear();
        BellRingerLoadoutService.removeBells(player);
    }
}
