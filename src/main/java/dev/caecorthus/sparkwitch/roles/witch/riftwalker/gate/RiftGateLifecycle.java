package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerMatch;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Gate lifecycle (plan §5.2): sweeps at {@code ON_GAME_START}, {@code ON_FINISH_INITIALIZE},
 * {@code ON_FINISH_FINALIZE} and {@code SERVER_STOPPED} over every server world (map voting moves players between
 * dimensions), registry repair for lost entities, and the per-tick entity self-check. Gates survive their placer's
 * death (D4). Owned by P1.
 * 门的生命周期（plan §5.2）：在 {@code ON_GAME_START}、{@code ON_FINISH_INITIALIZE}、{@code ON_FINISH_FINALIZE} 与
 * {@code SERVER_STOPPED} 清扫所有服务端世界（地图投票会在维度间移动玩家），修复丢失实体的登记，以及实体每 tick 的自检。
 * 放置者死亡后门保留（D4）。归属 P1。
 *
 * <p>D4: there is deliberately no death, disconnect or role-change listener. Gates are faction property; they leave
 * only through the tablet console ({@link RiftGateRegistry#close}) or these round-edge sweeps, which are silent (the
 * session service ends its own sessions at round end, without cooldown).
 * D4：刻意不监听死亡、断线或职业变更。门是阵营资产，只会经平板控制台（{@link RiftGateRegistry#close}）或这些对局边界
 * 清扫离开；清扫是静默的（会话服务在对局结束时自行结束会话，不上冷却）。
 */
public final class RiftGateLifecycle {
    private static boolean registered;
    private static volatile @Nullable MinecraftServer server;

    private RiftGateLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            // Worlds are closed: forget records only. / 世界已关闭：只遗忘记录。
            for (ServerWorld world : stopped.getWorlds()) {
                RiftGateRegistry.forget(world);
            }
            server = null;
        });

        // Wathe never removes custom entities: stray gates of an aborted round never enter the new one.
        // Wathe 从不移除自定义实体：中止回合残留的门不会进入新回合。
        GameEvents.ON_GAME_START.register(gameMode -> {
            MinecraftServer current = server;
            if (current != null) {
                sweepAll(current);
            }
        });
        // Numbering restarts at 1 for the new round (C9). / 新回合编号从 1 重新开始（C9）。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                sweepAll(serverWorld.getServer());
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                sweepAll(serverWorld.getServer());
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(RiftGateRegistry::repair);
        RiftGateReplayFormatters.register();
    }

    /** Silent clear of every server world (records, tickets, entities). / 静默清理所有服务端世界（记录、区块票、实体）。 */
    static void sweepAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            RiftGateRegistry.clear(world);
        }
    }

    /**
     * Server self-check run by {@link RiftGateEntity#tick}: false discards the entity (round not running, match id
     * differs, or the registry no longer lists this gate).
     * 由 {@link RiftGateEntity#tick} 调用的服务端自检：返回 false 时移除实体（对局未进行、对局 id 不符或登记表已不含此门）。
     */
    public static boolean passesSelfCheck(ServerWorld world, RiftGateEntity gate) {
        return RiftGateLifecycleRules.passesSelfCheck(GameWorldComponent.KEY.get(world).isRunning(), gate.matchId(),
                RiftwalkerMatch.currentMatchId(world), RiftGateRegistry.isListed(world, gate));
    }
}
