package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread ring buffer of every live crow's box at the end of each server tick: the crow's counterpart of
 * {@code PlayerHitboxHistory}, so hitscan weapons and client-picked shots are lag-compensated against a target that
 * flies up to 24 blocks/s (a shooter aims at its delayed client view: tracker interval 1, 3-step interpolation, plus
 * the round trip). In-memory and transient (no NBT, sync or packets); a crow's samples are dropped the tick it is no
 * longer live. Volumes reuse {@link HitscanLagRules}: the shooter's ping-based rewind window, swept between ticks.
 * 仅在服务端线程维护的环形缓冲，记录每只存活乌鸦在每个服务端刻结束时的箱体：即乌鸦版的 {@code PlayerHitboxHistory}，
 * 使即时射线武器与客户端选靶的射击能对最高 24 格/秒飞行的目标做延迟补偿（射手瞄准的是延迟后的客户端画面：追踪间隔 1、
 * 3 步插值，再加往返延迟）。纯内存、临时（无 NBT、同步或数据包）；乌鸦不再存活的那一刻即丢弃其样本。体积复用
 * {@link HitscanLagRules}：按射手延迟计算回溯窗口，并在相邻刻之间扫掠。
 */
public final class BlackRavenCrowHitboxHistory {
    private static final Map<UUID, ArrayDeque<Sample>> HISTORY = new HashMap<>();
    private static boolean registered;

    private record Sample(int tick, Box box) {
    }

    private BlackRavenCrowHitboxHistory() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(BlackRavenCrowHitboxHistory::record);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HISTORY.clear());
    }

    /**
     * Lag-compensated hit volumes of {@code crow} as {@code shooter} saw it: the current box plus the recorded boxes
     * inside the shooter's rewind window, swept between ticks and grown by the weapon's {@code growth} only.
     * {@code shooter} 所见的 {@code crow} 延迟补偿命中体积：当前箱体加射手回溯窗口内的历史箱体，在相邻刻之间扫掠，
     * 只按武器自身的 {@code growth} 扩大。
     */
    public static List<Box> hitVolumes(ServerPlayerEntity shooter, BlackRavenCrowEntity crow, double growth) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(crow.getBoundingBox());
        ArrayDeque<Sample> samples = HISTORY.get(crow.getUuid());
        MinecraftServer server = shooter.getServer();
        if (samples != null && server != null) {
            int oldest = server.getTicks() - HitscanLagRules.rewindTicks(shooter.networkHandler.getLatency());
            for (Sample sample : samples) {
                if (sample.tick() < oldest) {
                    break;
                }
                boxes.add(sample.box());
            }
        }
        return HitscanLagRules.sweptVolumes(boxes, growth);
    }

    private static void record(MinecraftServer server) {
        int tick = server.getTicks();
        Set<UUID> live = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
            if (crow == null) {
                continue;
            }
            live.add(crow.getUuid());
            ArrayDeque<Sample> samples = HISTORY.computeIfAbsent(crow.getUuid(), uuid -> new ArrayDeque<>());
            samples.addFirst(new Sample(tick, crow.getBoundingBox()));
            while (samples.size() > HitscanLagRules.HISTORY_TICKS) {
                samples.removeLast();
            }
        }
        HISTORY.keySet().retainAll(live);
    }
}
