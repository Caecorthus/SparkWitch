package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
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
 * Unlimited remote range, server side (owner decision 2026-10-04): the owner's view of a far car or camera reaches the
 * client although the server player (the body) never moves. Server only, server thread only, nothing synced or saved.
 * 无限遥控距离的服务端部分（所有者 2026-10-04 决定）：即使服务端玩家（本体）从不移动，拥有者也能看到远处小车或摄像头的画面。
 * 仅服务端、仅服务端线程，不同步也不存盘。
 *
 * <ul>
 *   <li>Focus streaming: while {@link #focusOf} names a device, an expiring {@link #FOCUS_TICKET} around it (radius
 *   {@link SeekerRemoteStreamingRules#focusTicketRadius}) keeps its surroundings loaded and entity-ticking (tracker
 *   updates run only in entity-ticking chunks), and {@code mixin/seeker/SeekerRemote*} centre the owner's chunk view,
 *   chunk batches and entity tracking on the device. The body sends no movement packets, so the owner's tracking is
 *   re-evaluated ({@code updatePosition}) every tick, and for {@link SeekerRemoteStreamingRules#RESETTLE_TICKS} after
 *   the session so the view settles back on the body. Player chunk tickets stay keyed on the body's real section, so
 *   the body's chunks stay loaded and ticking on the server.
 *   焦点流式加载：{@link #focusOf} 返回设备期间，设备周围一张会过期的 {@link #FOCUS_TICKET} 保持区块加载并处理实体
 *   （追踪器只在处理实体的区块中更新），{@code mixin/seeker/SeekerRemote*} 以设备为中心计算拥有者的区块视野、区块批次与实体追踪。
 *   本体不发送移动包，因此每刻重新评估拥有者的追踪，并在会话结束后继续 RESETTLE_TICKS 刻，使视野回到本体。
 *   玩家区块票据仍以本体真实所在区段为准，服务端上本体所在区块始终保持加载并 tick。</li>
 *   <li>Idle keep-alive: devices never save to disk, so a device whose chunk unloads is lost. While its owner is the
 *   owner of record of the running round, every referenced live device holds an expiring {@link #DEVICE_TICKET} (car:
 *   entity-ticking; camera: loaded only). The sweeps at game start and finalize remove the devices and the finalize
 *   cleanup clears the component, so nothing is refreshed afterwards and the tickets expire by themselves.
 *   空闲保活：设备从不存盘，所在区块卸载即丢失。拥有者为进行中回合的记录拥有者时，每台仍被引用的存活设备持有一张会过期的
 *   {@link #DEVICE_TICKET}（小车：处理实体；摄像头：仅保持加载）。开局与结算清扫会移除设备，结算清理会清空组件，
 *   此后不再刷新，票据自行过期。</li>
 * </ul>
 *
 * <p>Tick order: {@link #tick} runs on {@code END_WORLD_TICK}, after every entity (and so every Seeker component's
 * session checks, device-existence step and battery) ticked, so a session or device that ended this tick is never
 * refreshed. {@link #onSessionOpened} streams a newly opened or atomically switched focus at once.
 * 刻顺序：{@link #tick} 在 {@code END_WORLD_TICK} 运行，此时所有实体（以及每个搜寻者组件的会话检查、设备存在性与电量步骤）
 * 都已 tick，因此本刻已结束的会话或设备不会被刷新。{@link #onSessionOpened} 会立即为新打开或原子切换的焦点开始流式加载。</p>
 */
public final class SeekerRemoteStreaming {
    /**
     * Around the session focus; argument: the focus entity id. Distinct from SparkStrength's drone tickets.
     * 位于会话焦点周围；参数为焦点实体 id。与 SparkStrength 的无人机票据互不相同。
     */
    private static final ChunkTicketType<Integer> FOCUS_TICKET = ChunkTicketType.create(
            SparkWitch.MOD_ID + ":seeker_remote", Integer::compare, SeekerRemoteStreamingRules.TICKET_EXPIRY_TICKS);
    /** Idle keep-alive on each live device; argument: the device entity id. / 每台存活设备的空闲保活；参数为设备实体 id。 */
    private static final ChunkTicketType<Integer> DEVICE_TICKET = ChunkTicketType.create(
            SparkWitch.MOD_ID + ":seeker_device", Integer::compare, SeekerRemoteStreamingRules.TICKET_EXPIRY_TICKS);

    /** Owners whose view was centred on a focus at the last refresh. / 上次刷新时视野以焦点为中心的拥有者。 */
    private static final Set<UUID> STREAMING = new HashSet<>();
    /** Owner uuid -> remaining post-session re-evaluation ticks. / 拥有者 uuid -> 会话结束后剩余的重新评估刻数。 */
    private static final Map<UUID, Integer> RESETTLING = new HashMap<>();
    private static boolean registered;

    private SeekerRemoteStreaming() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(SeekerRemoteStreaming::tick);
        // A disconnected owner's view is gone with its connection; its device tickets simply expire.
        // 断线拥有者的视野随连接消失；其设备票据自行过期。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STREAMING.clear();
            RESETTLING.clear();
        });
    }

    /**
     * Hook at the end of {@code SeekerRemoteSessionService#open}: streams a newly opened or atomically switched focus
     * in the same server task as the session sync, so the device's spawn packet follows the sync immediately.
     * {@code SeekerRemoteSessionService#open} 末尾的钩子：在与会话同步相同的服务端任务中为新打开或原子切换的焦点开始流式加载，
     * 使设备的生成包紧随同步发送。
     */
    public static void onSessionOpened(@Nullable ServerPlayerEntity owner) {
        SeekerDeviceEntity focus = owner == null ? null : liveFocus(owner);
        if (focus != null) {
            stream(owner, focus);
        }
    }

    /**
     * View-centre query for the {@code mixin/seeker/SeekerRemote*} streaming mixins: the device the owner's live session
     * shows, only while the owner is alive, not a spectator, still its own server camera (Taotie, Last Stand and other
     * server camera writers take precedence), and the session's server bookkeeping matches the synced focus. Null means
     * "use vanilla", so an end, a failed check or a switch in flight reverts by itself. Hot path: O(1) with no stream.
     * 供 {@code mixin/seeker/SeekerRemote*} 流式加载 mixin 查询视野中心：拥有者存活会话正在显示的设备，前提是拥有者存活、
     * 不是旁观者、服务端相机仍是自身（饕餮、背水一战等服务端相机写入方优先），且会话的服务端记录与同步焦点一致。
     * null 表示使用原版行为，因此结束、校验失败或切换途中都会自行恢复。热路径：无流式加载时 O(1)。
     */
    @Nullable
    public static SeekerDeviceEntity focusOf(@Nullable ServerPlayerEntity owner) {
        if (STREAMING.isEmpty() || owner == null || !STREAMING.contains(owner.getUuid())) {
            return null;
        }
        return liveFocus(owner);
    }

    /** End of every world tick; see the class Javadoc for the order. / 每个世界刻末尾；顺序见类注释。 */
    static void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Non-Seekers hold no state and leave after two field reads; a live session is never idle.
            // 非搜寻者不持有状态，读取两个字段后即跳过；存活的会话绝不处于空闲状态。
            SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
            boolean holdsState = status != null && !status.state().isIdle();
            if (holdsState) {
                keepDevicesLoaded(player, status);
            }
            SeekerDeviceEntity focus = holdsState ? liveFocus(player) : null;
            if (focus != null) {
                stream(player, focus);
            } else if (!STREAMING.isEmpty() && STREAMING.remove(player.getUuid())) {
                RESETTLING.put(player.getUuid(), SeekerRemoteStreamingRules.RESETTLE_TICKS);
            }
        }
        if (!RESETTLING.isEmpty()) {
            resettle(world);
        }
    }

    @Nullable
    private static SeekerDeviceEntity liveFocus(ServerPlayerEntity owner) {
        if (owner.isRemoved() || !owner.isAlive() || owner.isSpectator() || owner.getCameraEntity() != owner) {
            return null;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null) {
            return null;
        }
        SeekerSessionState session = status.sessionState();
        SeekerDeviceKind kind = SeekerRemoteStreamingRules.focusKind(status.sessionMode());
        int focusId = SeekerRemoteStreamingRules.expectedFocusId(status.state());
        if (session == null || kind == null || focusId < 0 || session.sessionId() != status.sessionId()
                || session.mode() != status.sessionMode() || session.focusEntityId() != focusId) {
            return null;
        }
        Entity entity = owner.getServerWorld().getEntityById(focusId);
        return entity instanceof SeekerDeviceEntity device && !device.isRemoved() && device.kind() == kind
                && device.isOwnedBy(owner.getUuid()) ? device : null;
    }

    /**
     * One tick of the far view: refresh the focus ticket and re-evaluate the owner's entity tracking around the focus
     * (the chunk view itself follows every tick through {@code sendWatchPackets}).
     * 远程视野的单刻处理：刷新焦点票据，并以焦点为准重新评估拥有者的实体追踪（区块视野本身每刻经 sendWatchPackets 跟随）。
     */
    private static void stream(ServerPlayerEntity owner, SeekerDeviceEntity focus) {
        STREAMING.add(owner.getUuid());
        RESETTLING.remove(owner.getUuid());
        ServerWorld world = owner.getServerWorld();
        int radius = SeekerRemoteStreamingRules.focusTicketRadius(
                world.getServer().getPlayerManager().getViewDistance(), owner.getViewDistance());
        world.getChunkManager().addTicket(FOCUS_TICKET, focus.getChunkPos(), radius, focus.getId());
        world.getChunkManager().updatePosition(owner);
    }

    /** Lets the view and tracking settle back on the body after a session. / 会话结束后让视野与追踪回到本体。 */
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

    /**
     * Refreshes the keep-alive ticket of every device the owner still references, only while the owner is the owner
     * of record of the running round (the same gate as the device self-check).
     * 刷新拥有者仍引用的每台设备的保活票据；仅当拥有者是进行中回合的记录拥有者时执行（与设备自检相同的门槛）。
     */
    private static void keepDevicesLoaded(ServerPlayerEntity owner, SeekerStatusComponent status) {
        if (!SeekerTargeting.isOwnerOfRecord(owner)) {
            return;
        }
        if (status.carState() == SeekerCarState.DEPLOYED) {
            hold(SeekerDeviceService.findCar(owner));
        }
        for (SeekerState.Camera camera : status.cameras()) {
            hold(SeekerDeviceService.findCamera(owner, camera.entityId()));
        }
    }

    private static void hold(@Nullable SeekerDeviceEntity device) {
        if (device != null && !device.isRemoved() && device.getWorld() instanceof ServerWorld world) {
            world.getChunkManager().addTicket(DEVICE_TICKET, device.getChunkPos(),
                    SeekerRemoteStreamingRules.keepAliveRadius(device.kind()), device.getId());
        }
    }

    private static void forget(UUID owner) {
        STREAMING.remove(owner);
        RESETTLING.remove(owner);
    }
}
