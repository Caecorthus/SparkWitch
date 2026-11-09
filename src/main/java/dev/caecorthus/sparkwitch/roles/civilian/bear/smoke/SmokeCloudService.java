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
import java.util.List;
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

    /**
     * Frozen contract (BEAR WP0, implemented by WP5): carves a temporary capsule-shaped tunnel of {@code radius} blocks
     * along the segment {@code from} → {@code to} (a hitscan ray through the smoke) into every cloud it crosses. WP3
     * calls it on every hitscan ray (radius 0.35) and along the potion backblast lane (0.5). Same as
     * {@link #carveTunnel(ServerWorld, Vec3d, Vec3d, float, UUID)} with the shooter unknown, so the rate limit is keyed
     * on the shot's origin.
     * 冻结契约（BEAR WP0，由 WP5 实现）：沿线段 {@code from} → {@code to}（穿过烟雾的命中扫描射线）在其经过的每个烟团中开出
     * 半径 {@code radius} 格的胶囊形临时通道。WP3 在每条命中扫描射线（半径 0.35）与药炮后喷通道（0.5）上调用。等同于射手未知
     * 的五参数版本，因此按射击起点限流。
     */
    public static void carveTunnel(ServerWorld world, Vec3d from, Vec3d to, float radius) {
        carveTunnel(world, from, to, radius, null);
    }

    /**
     * Carves a tunnel (see {@link #carveTunnel(ServerWorld, Vec3d, Vec3d, float)}) for a known {@code shooter}. Cheap
     * when there is no smoke: nothing is sent unless the segment crosses a live cloud's box (server AABB test); the
     * tunnel is clipped to each cloud it crosses (one capsule per cloud, touching ones joined, each reaching
     * {@link SmokeTunnels#CLIP_MARGIN} past the box), merged into a recent wider one it adds nothing to, limited to
     * {@link SmokeCloudRegistry#TUNNELS_PER_SHOOTER} per {@link SmokeCloudRegistry#TUNNEL_WINDOW_TICKS} per shooter (a
     * shotgun's pellets) and to {@link SmokeCloudRegistry#MAX_TUNNELS} per world (oldest dropped, never a blast hole).
     * A bullet tunnel lives {@link SmokeTimeline#TUNNEL_DURATION_TICKS}; a blast lane (radius at least
     * {@link SmokeTimeline#BLAST_LANE_RADIUS}) lives as long as a blast hole. Visual only, like all smoke.
     * 为已知射手 {@code shooter} 开出通道（见四参数版本）。无烟时开销极低：线段不穿过存活烟团的盒子（服务端 AABB 检测）就什么
     * 都不发送；通道被裁剪到其穿过的每团烟（每团一条胶囊，相接的合并，各自越过盒子 {@link SmokeTunnels#CLIP_MARGIN}），
     * 若对近期更宽的通道毫无增益则并入它，每名射手每
     * {@link SmokeCloudRegistry#TUNNEL_WINDOW_TICKS} 至多 {@link SmokeCloudRegistry#TUNNELS_PER_SHOOTER} 条（霰弹枪的弹丸），
     * 每个世界至多 {@link SmokeCloudRegistry#MAX_TUNNELS} 条（丢弃最早的，绝不挤掉爆炸空洞）。子弹通道存在
     * {@link SmokeTimeline#TUNNEL_DURATION_TICKS}；爆炸通道（半径至少 {@link SmokeTimeline#BLAST_LANE_RADIUS}）与爆炸空洞同寿命。
     * 与所有烟雾一样纯属视觉。
     */
    public static void carveTunnel(ServerWorld world, Vec3d from, Vec3d to, float radius, @Nullable UUID shooter) {
        if (world == null || !finite(from) || !finite(to) || !(radius > 0.0F) || !Float.isFinite(radius)) {
            return;
        }
        SmokeCloudRegistry registry = WORLDS.get(world);
        if (registry == null) {
            return;
        }
        long now = world.getTime();
        float clamped = Math.min(radius, SmokeHoleS2CPayload.MAX_RADIUS);
        List<SmokeHole> pieces = registry.clipTunnel(from.x, from.y, from.z, to.x, to.y, to.z, clamped, now,
                SmokeTimeline.tunnelDurationTicks(clamped));
        if (pieces.isEmpty()) {
            return;
        }
        Object key = shooter != null ? shooter : SmokeCloudRegistry.originKey(from.x, from.y, from.z);
        for (SmokeHole tunnel : registry.addTunnels(pieces, key, now)) {
            SmokeNetworking.sendToWorld(world, new SmokeHoleS2CPayload(tunnel));
        }
    }

    private static boolean finite(@Nullable Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
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
        for (SmokeHole tunnel : registry.tunnels()) {
            SmokeNetworking.send(player, new SmokeHoleS2CPayload(tunnel));
        }
    }

    /**
     * True while the world has a live cloud; ranged sources use it to skip ray work when nothing can be carved.
     * 世界中存在存活烟团时为 true；远程来源据此在无可开洞时跳过射线计算。
     */
    public static boolean hasSmoke(ServerWorld world) {
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
