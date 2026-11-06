package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeClearS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeCloudRemoveS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeCloudS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeHoleS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeNetworking;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frozen contract (server only): the single entry point that bursts, carves and clears BEAR smoke. The smoke round
 * entity, explosions and the round lifecycle call only these methods; the shape, caps and client sync live behind it.
 * Server thread only. The shape is computed once at the burst ({@link SmokeCloudShape}) and sent once to every player
 * in the world; the caps and expiry live in a per-world {@link SmokeCloudRegistry}; {@link SmokeCloudLifecycle}
 * ticks it, resyncs joining, respawning and world-changing players, and clears it at round start and end. Smoke is
 * purely visual: no server rule, line of sight included, ever reads it (owner 2026-11-05).
 * 冻结契约（仅服务端）：BEAR 烟雾爆开、开洞与清除的唯一入口。烟雾榴弹实体、各种爆炸与回合生命周期只调用这些方法；
 * 形状、上限与客户端同步都在其后。仅限服务端线程。形状在爆开时一次算好（{@link SmokeCloudShape}），并一次发给该世界的所有
 * 玩家；上限与过期由每个世界的 {@link SmokeCloudRegistry} 管理；{@link SmokeCloudLifecycle} 负责推进、为进服、重生与
 * 换世界的玩家重新同步，并在回合开始与结束时清除。烟雾纯属视觉：任何服务端规则（包括视线）都不读取它（所有者 2026-11-05）。
 */
public final class SmokeCloudService {
    private static final Map<ServerWorld, SmokeCloudRegistry> WORLDS = new HashMap<>();
    /** Unique per server run; 0 is never used ({@link SmokeCloudRemoveS2CPayload#INVALID_ID}). / 每次服务端运行内唯一；从不使用 0。 */
    private static int nextCloudId = 1;

    private SmokeCloudService() {
    }

    /**
     * Bursts a cloud at {@code impact}. {@code incomingDirection} (may be null) is the round's flight direction and is
     * used to step the seed back out of a block it hit. Returns the new cloud id, or -1 when nothing was spawned.
     * 在 {@code impact} 处爆开一团烟。{@code incomingDirection}（可为 null）是榴弹飞行方向，用于把撞进方块的起点退回空处。
     * 返回新烟团 id；未生成时返回 -1。
     */
    public static int spawn(ServerWorld world, Vec3d impact, @Nullable Vec3d incomingDirection, @Nullable UUID owner) {
        if (world == null || impact == null || !Double.isFinite(impact.x) || !Double.isFinite(impact.y)
                || !Double.isFinite(impact.z)) {
            return -1;
        }
        long now = world.getTime();
        SmokeCloudSnapshot cloud = SmokeCloudShape.build(nextCloudId, now, impact, incomingDirection,
                new WorldSmokeBlockView(world));
        if (cloud == null) {
            return -1;
        }
        nextCloudId = nextCloudId == Integer.MAX_VALUE ? 1 : nextCloudId + 1;
        SmokeCloudRegistry registry = WORLDS.computeIfAbsent(world, ignored -> new SmokeCloudRegistry());
        SmokeCloudRegistry.Changes changes = registry.add(cloud, owner, now);
        sendRemovals(world, changes.removed());
        for (SmokeCloudSnapshot replaced : changes.replaced()) {
            SmokeNetworking.sendToWorld(world, SmokeCloudS2CPayload.of(replaced));
        }
        SmokeNetworking.sendToWorld(world, SmokeCloudS2CPayload.of(cloud));
        return cloud.cloudId();
    }

    /**
     * Punches a temporary hole of {@code radius} blocks into every cloud near {@code center}.
     * 在 {@code center} 附近所有烟团中炸开半径 {@code radius} 格的临时空洞。
     */
    public static void carveHole(ServerWorld world, Vec3d center, float radius) {
        if (world == null || center == null || !(radius > 0.0F) || !Double.isFinite(center.x)
                || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            return;
        }
        SmokeCloudRegistry registry = WORLDS.get(world);
        if (registry == null) {
            return;
        }
        long now = world.getTime();
        SmokeHole hole = new SmokeHole(center.x, center.y, center.z, Math.min(radius, SmokeHoleS2CPayload.MAX_RADIUS),
                now, SmokeTimeline.HOLE_DURATION_TICKS);
        if (registry.addHole(hole, now)) {
            SmokeNetworking.sendToWorld(world, new SmokeHoleS2CPayload(hole));
        }
    }

    /** Removes every cloud in the world and tells clients. / 清除该世界所有烟团并通知客户端。 */
    public static void clearAll(ServerWorld world) {
        SmokeCloudRegistry registry = WORLDS.remove(world);
        if (registry != null && !registry.isEmpty()) {
            SmokeNetworking.sendToWorld(world, SmokeClearS2CPayload.INSTANCE);
        }
    }

    /** End of each world tick: drops ended clouds and holes. / 每个世界 tick 末尾：丢弃已结束的烟团与空洞。 */
    static void tick(ServerWorld world) {
        SmokeCloudRegistry registry = WORLDS.get(world);
        if (registry == null) {
            return;
        }
        sendRemovals(world, registry.expire(world.getTime()));
        if (registry.isEmpty()) {
            WORLDS.remove(world);
        }
    }

    /**
     * Sends the world's live clouds and holes to one player, after a clear when the client may still hold another
     * world's smoke. / 把该世界存活的烟团与空洞发给一名玩家；客户端可能仍持有其他世界的烟时先发清除。
     */
    static void syncTo(ServerPlayerEntity player, ServerWorld world, boolean clearFirst) {
        if (clearFirst) {
            SmokeNetworking.send(player, SmokeClearS2CPayload.INSTANCE);
        }
        SmokeCloudRegistry registry = WORLDS.get(world);
        if (registry == null) {
            return;
        }
        for (SmokeCloudSnapshot cloud : registry.snapshots()) {
            SmokeNetworking.send(player, SmokeCloudS2CPayload.of(cloud));
        }
        for (SmokeHole hole : registry.holes()) {
            SmokeNetworking.send(player, new SmokeHoleS2CPayload(hole));
        }
    }

    static boolean hasSmoke(ServerWorld world) {
        SmokeCloudRegistry registry = WORLDS.get(world);
        return registry != null && !registry.isEmpty();
    }

    /** An unloading world has no player left to tell. / 正在卸载的世界已没有需要通知的玩家。 */
    static void forgetWorld(ServerWorld world) {
        WORLDS.remove(world);
    }

    static void forgetAll() {
        WORLDS.clear();
    }

    private static void sendRemovals(ServerWorld world, IntList removed) {
        for (int i = 0; i < removed.size(); i++) {
            SmokeNetworking.sendToWorld(world, new SmokeCloudRemoveS2CPayload(removed.getInt(i)));
        }
    }
}
