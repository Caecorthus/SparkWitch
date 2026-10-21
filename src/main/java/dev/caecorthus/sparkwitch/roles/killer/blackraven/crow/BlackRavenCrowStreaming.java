package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Raven Form far view, server side, copied from the Seeker remote view ({@code SeekerRemoteStreaming}): the crow flies
 * away while the server player (the body) never moves, so the owner's chunk view, chunk batches and entity tracking are
 * centred on the crow by {@code mixin/blackraven/BlackRavenCrow*Mixin}, and an expiring {@link #CROW_TICKET} refreshed
 * every tick keeps the crow's surroundings loaded and entity-ticking. Player chunk tickets stay on the body's real
 * section, so the body's chunks stay loaded on the server. The motionless body sends no movement packets, so the
 * owner's tracking is re-evaluated every tick, and for {@link BlackRavenCrowRules#RESETTLE_TICKS} after the session so
 * the view settles back on the body. Server thread only; nothing synced or saved.
 * 化鸦远程视野的服务端部分，照搬搜寻者遥控视角：乌鸦飞走而服务端玩家（本体）不动，因此由
 * {@code mixin/blackraven/BlackRavenCrow*Mixin} 以乌鸦为中心计算主人的区块视野、区块批次与实体追踪，并每刻刷新一张会过期的
 * {@link #CROW_TICKET}，保持乌鸦周围区块加载并处理实体。玩家区块票据仍位于本体真实所在区段，服务端上本体区块保持加载。静止的本体
 * 不发送移动包，因此每刻重新评估主人的追踪，并在会话结束后继续 RESETTLE_TICKS 刻，使视野回到本体。仅服务端线程，不同步也不存盘。
 *
 * <p>Tick order: {@link #tick} runs on {@code END_WORLD_TICK}, after every player's component tick ended or kept the
 * session, so an ended session is never refreshed. {@link #onSessionStarted} streams a new crow at once.
 * 刻顺序：{@link #tick} 在 {@code END_WORLD_TICK} 运行，此时每名玩家的组件刻都已结束或保留会话，因此已结束的会话不会被刷新。
 * {@link #onSessionStarted} 会立即为新乌鸦开始流式加载。</p>
 */
public final class BlackRavenCrowStreaming {
    /** Around the flown crow; argument: the crow entity id. / 位于被操控乌鸦周围；参数为乌鸦实体 id。 */
    private static final ChunkTicketType<Integer> CROW_TICKET = ChunkTicketType.create(
            SparkWitch.MOD_ID + ":black_raven_crow", Integer::compare, BlackRavenCrowRules.TICKET_EXPIRY_TICKS);

    /** Owners whose view was centred on a crow at the last refresh. / 上次刷新时视野以乌鸦为中心的主人。 */
    private static final Set<UUID> STREAMING = new HashSet<>();
    /** Owner uuid -> remaining post-session re-evaluation ticks. / 主人 uuid -> 会话结束后剩余的重新评估刻数。 */
    private static final Map<UUID, Integer> RESETTLING = new HashMap<>();
    private static boolean registered;

    private BlackRavenCrowStreaming() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(BlackRavenCrowStreaming::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STREAMING.clear();
            RESETTLING.clear();
        });
    }

    /**
     * View-centre query for the {@code mixin/blackraven/BlackRavenCrow*} streaming mixins: the crow the owner's live
     * session flies, only while the owner is alive, not a spectator and still its own server camera (Taotie and other
     * server camera writers take precedence). Null means "use vanilla". Hot path: O(1) while nobody flies.
     * 供 {@code mixin/blackraven/BlackRavenCrow*} 流式加载 mixin 查询视野中心：主人有效会话正在操控的乌鸦，前提是主人存活、
     * 不是旁观者，且服务端相机仍是自身（饕餮等服务端相机写入方优先）。null 表示使用原版行为。热路径：无人飞行时 O(1)。
     */
    public static @Nullable BlackRavenCrowEntity focusOf(@Nullable ServerPlayerEntity owner) {
        if (STREAMING.isEmpty() || owner == null || !STREAMING.contains(owner.getUuid())) {
            return null;
        }
        return liveFocus(owner);
    }

    /** Hook at the end of a session start. / 会话开始末尾的钩子。 */
    static void onSessionStarted(ServerPlayerEntity owner, BlackRavenCrowEntity crow) {
        if (liveFocus(owner) == crow) {
            stream(owner, crow);
        }
    }

    /** Hook at a session end: the view settles back on the body. / 会话结束时的钩子：视野回到本体。 */
    static void onSessionEnded(ServerPlayerEntity owner) {
        if (STREAMING.remove(owner.getUuid())) {
            RESETTLING.put(owner.getUuid(), BlackRavenCrowRules.RESETTLE_TICKS);
        }
    }

    static void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlackRavenCrowEntity focus = BlackRavenCrowForm.isInCrowForm(player) ? liveFocus(player) : null;
            if (focus != null) {
                stream(player, focus);
            } else if (!STREAMING.isEmpty() && STREAMING.remove(player.getUuid())) {
                RESETTLING.put(player.getUuid(), BlackRavenCrowRules.RESETTLE_TICKS);
            }
        }
        if (!RESETTLING.isEmpty()) {
            resettle(world);
        }
    }

    private static @Nullable BlackRavenCrowEntity liveFocus(ServerPlayerEntity owner) {
        if (owner.isRemoved() || !owner.isAlive() || owner.isSpectator() || owner.getCameraEntity() != owner) {
            return null;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(owner);
        return crow != null && crow.getWorld() == owner.getWorld() ? crow : null;
    }

    /** One tick of the far view: refresh the ticket and re-evaluate tracking. / 远程视野的单刻处理。 */
    private static void stream(ServerPlayerEntity owner, BlackRavenCrowEntity crow) {
        STREAMING.add(owner.getUuid());
        RESETTLING.remove(owner.getUuid());
        ServerWorld world = owner.getServerWorld();
        int radius = BlackRavenCrowRules.focusTicketRadius(
                world.getServer().getPlayerManager().getViewDistance(), owner.getViewDistance());
        world.getChunkManager().addTicket(CROW_TICKET, crow.getChunkPos(), radius, crow.getId());
        world.getChunkManager().updatePosition(owner);
    }

    private static void resettle(ServerWorld world) {
        Iterator<Map.Entry<UUID, Integer>> iterator = RESETTLING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.getServerWorld() != world) {
                continue;
            }
            world.getChunkManager().updatePosition(player);
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private static void forget(UUID owner) {
        STREAMING.remove(owner);
        RESETTLING.remove(owner);
    }
}
