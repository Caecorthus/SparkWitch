package dev.caecorthus.sparkwitch.util.hitscan;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread ring buffer of every player's hitbox at the end of each server tick, used only by lag-compensated
 * hitscan weapons. In-memory and transient: no NBT, no sync, no packets; dropped on disconnect and server stop.
 * Client paths (crosshair hints) keep using current boxes, because the client already sees the delayed positions.
 * 仅在服务端线程维护的环形缓冲，记录每名玩家在每个服务端刻结束时的碰撞箱，只供延迟补偿的即时射线武器使用。
 * 纯内存、临时：不写 NBT、不同步、不发数据包；断线与服务端停止时清除。客户端路径（准星提示）仍使用当前箱体，
 * 因为客户端看到的本就是延迟后的位置。
 */
public final class PlayerHitboxHistory {
    private static final Map<UUID, ArrayDeque<Sample>> HISTORY = new HashMap<>();
    private static boolean registered;

    private PlayerHitboxHistory() {
    }

    private record Sample(int tick, RegistryKey<World> world, Box box) {
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
     * Newest first: the current box, then recorded boxes no older than the shooter's rewind window, stopping at a
     * dimension change. / 最新在前：当前箱体，然后是不早于射手回溯窗口的历史箱体，遇到维度变化即停止。
     */
    static List<Box> recentBoxes(ServerPlayerEntity shooter, PlayerEntity target) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(target.getBoundingBox());
        ArrayDeque<Sample> samples = HISTORY.get(target.getUuid());
        MinecraftServer server = shooter.getServer();
        if (samples == null || server == null) {
            return boxes;
        }
        int oldestTick = server.getTicks() - HitscanLagRules.rewindTicks(shooter.networkHandler.getLatency());
        RegistryKey<World> world = target.getWorld().getRegistryKey();
        for (Sample sample : samples) {
            if (sample.tick() < oldestTick || !sample.world().equals(world)) {
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
            samples.addFirst(new Sample(tick, player.getWorld().getRegistryKey(), player.getBoundingBox()));
            while (samples.size() > HitscanLagRules.HISTORY_TICKS) {
                samples.removeLast();
            }
        }
    }
}
