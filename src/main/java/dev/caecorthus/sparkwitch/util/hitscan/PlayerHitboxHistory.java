package dev.caecorthus.sparkwitch.util.hitscan;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread ring buffer of every player's hitbox at the end of each server tick, used only by lag-compensated
 * hitscan weapons and the Potion Gunner shell's in-flight player check. In-memory and transient: no NBT, no sync, no
 * packets; dropped on disconnect and server stop.
 * Client paths (crosshair hints) keep using current boxes, because the client already sees the delayed positions.
 * 仅在服务端线程维护的环形缓冲，记录每名玩家在每个服务端刻结束时的碰撞箱，只供延迟补偿的即时射线武器与药炮手炮弹的
 * 飞行中玩家判定使用。
 * 纯内存、临时：不写 NBT、不同步、不发数据包；断线与服务端停止时清除。客户端路径（准星提示）仍使用当前箱体，
 * 因为客户端看到的本就是延迟后的位置。
 */
public final class PlayerHitboxHistory {
    private static final Map<UUID, ArrayDeque<Sample>> HISTORY = new HashMap<>();
    private static boolean registered;

    private PlayerHitboxHistory() {
    }

    /**
     * {@code hittable}: neither spectator nor creative nor under SparkTraits Last Escape when recorded. Wathe-dead
     * players, Rift Gate occupants and swallowed players are spectators, so their boxes from that time are never
     * projectile hit volumes.
     * {@code hittable}：记录时既非旁观、也非创造模式、也不处于 SparkTraits 最后逃脱。Wathe 判定死亡的玩家、裂隙门内的玩家
     * 与被吞下的玩家都是旁观者，因此他们那段时间的箱体永远不会成为投射物命中体积。
     */
    private record Sample(int tick, RegistryKey<World> world, Box box, boolean hittable) {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(PlayerHitboxHistory::record);
        // Fabric may fire DISCONNECT on the Netty thread; the map is only touched on the server thread.
        // Fabric 可能在 Netty 线程触发 DISCONNECT；该映射只在服务端线程访问。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            server.execute(() -> HISTORY.remove(uuid));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HISTORY.clear());
    }

    /**
     * Lag-compensated hit volumes of {@code target} as {@code shooter} saw it: the current box plus the recorded boxes
     * inside the shooter's rewind window, swept between ticks and grown by the weapon's {@code baseExpansion} only.
     * {@code shooter} 所见的 {@code target} 延迟补偿命中体积：当前箱体加射手回溯窗口内的历史箱体，在相邻刻之间扫掠，
     * 只按武器自身的 {@code baseExpansion} 扩大。
     */
    public static List<Box> hitVolumes(ServerPlayerEntity shooter, PlayerEntity target, double baseExpansion) {
        return HitscanLagRules.sweptVolumes(recentBoxes(shooter, target), baseExpansion);
    }

    /**
     * Lag-compensated hit volumes of {@code target} for a projectile that its shooter's client also simulates (the
     * Potion Gunner shell). That client draws the projectile and the other players both about half a round trip late,
     * so the ping cancels out: only the ping-independent view delay
     * ({@link HitscanLagRules#CLIENT_VIEW_DELAY_TICKS}) is rewound, counted back from the tick being checked. The
     * history also stops at the first box the target could not be hit in, and at the first jump
     * ({@link HitscanLagRules#connectedPrefix}), so a projectile never hits a spot the target left by teleport or gate.
     * 射手客户端同样会模拟的投射物（药炮手炮弹）对 {@code target} 的延迟补偿命中体积。该客户端绘制投射物与其他玩家时
     * 都约晚半个往返延迟，二者相互抵消：只回溯与延迟无关的视图滞后（{@link HitscanLagRules#CLIENT_VIEW_DELAY_TICKS}），
     * 从正在检测的这一刻往回计算。历史还会在第一个目标不可被命中的箱体处、以及第一次跳变处
     * （{@link HitscanLagRules#connectedPrefix}）停止，因此投射物不会命中目标经传送或裂隙门离开的位置。
     */
    public static List<Box> projectileHitVolumes(PlayerEntity target, double baseExpansion) {
        return HitscanLagRules.sweptVolumes(HitscanLagRules.connectedPrefix(
                recentBoxes(target.getServer(), target, HitscanLagRules.CLIENT_VIEW_DELAY_TICKS, true)),
                baseExpansion);
    }

    /**
     * Newest first: the current box, then recorded boxes no older than the shooter's rewind window, stopping at a
     * dimension change. / 最新在前：当前箱体，然后是不早于射手回溯窗口的历史箱体，遇到维度变化即停止。
     */
    static List<Box> recentBoxes(ServerPlayerEntity shooter, PlayerEntity target) {
        return recentBoxes(shooter.getServer(), target,
                HitscanLagRules.rewindTicks(shooter.networkHandler.getLatency()), false);
    }

    private static List<Box> recentBoxes(@Nullable MinecraftServer server, PlayerEntity target, int rewindTicks,
                                         boolean hittableOnly) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(target.getBoundingBox());
        ArrayDeque<Sample> samples = HISTORY.get(target.getUuid());
        if (samples == null || server == null) {
            return boxes;
        }
        int oldestTick = server.getTicks() - rewindTicks;
        RegistryKey<World> world = target.getWorld().getRegistryKey();
        for (Sample sample : samples) {
            if (sample.tick() < oldestTick || !sample.world().equals(world) || (hittableOnly && !sample.hittable())) {
                break;
            }
            boxes.add(sample.box());
        }
        return boxes;
    }

    private static void record(MinecraftServer server) {
        int tick = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ArrayDeque<Sample> samples = HISTORY.computeIfAbsent(player.getUuid(), uuid -> new ArrayDeque<>());
            samples.addFirst(new Sample(tick, player.getWorld().getRegistryKey(), player.getBoundingBox(),
                    !player.isSpectator() && !player.isCreative()
                            && !SparkTraitsKillerBridge.isLastEscapeActive(player)));
            while (samples.size() > HitscanLagRules.HISTORY_TICKS) {
                samples.removeLast();
            }
        }
    }
}
