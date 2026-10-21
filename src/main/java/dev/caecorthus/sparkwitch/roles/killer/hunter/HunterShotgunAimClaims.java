package dev.caecorthus.sparkwitch.roles.killer.hunter;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server end of the shotgun aim claim ({@link HunterShotgunAim}): one pending claim per player, stored by the
 * {@code sparkwitch:double_barrel_aim} receiver and taken by the very next shotgun {@code use}. Both run on the server
 * thread, in packet order (Fabric queues custom payloads on the same task queue as vanilla packets). In-memory only:
 * no NBT, no sync; dropped on disconnect and server stop.
 * 猎枪瞄准声明（{@link HunterShotgunAim}）的服务端：每名玩家一个待处理声明，由 {@code sparkwitch:double_barrel_aim}
 * 接收器存入，并被紧随其后的猎枪 {@code use} 取走。二者都按数据包顺序在服务端线程运行（Fabric 将自定义载荷与原版
 * 数据包排入同一任务队列）。纯内存：不写 NBT、不同步；断线与服务端停止时清除。
 */
public final class HunterShotgunAimClaims {
    private static final Map<UUID, Claim> PENDING = new HashMap<>();
    private static boolean registered;

    private HunterShotgunAimClaims() {
    }

    private record Claim(int tick, List<Integer> targetIds) {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Fabric may fire DISCONNECT on the Netty thread; the map is only touched on the server thread.
        // Fabric 可能在 Netty 线程触发 DISCONNECT；该映射只在服务端线程访问。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            server.execute(() -> PENDING.remove(uuid));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /** Receiver side: replaces this player's pending claim. / 接收器：替换该玩家的待处理声明。 */
    public static void submit(ServerPlayerEntity player, List<Integer> targetIds) {
        PENDING.put(player.getUuid(), new Claim(player.getServer().getTicks(), List.copyOf(targetIds)));
    }

    /**
     * Removes this player's pending claim and returns its ids when still fresh, else null (no claim: the server pick).
     * 移除该玩家的待处理声明，仍新鲜时返回其 id，否则返回 null（无声明：使用服务端选取）。
     */
    public static @Nullable List<Integer> take(ServerPlayerEntity player) {
        Claim claim = PENDING.remove(player.getUuid());
        return claim != null && HunterShotgunAim.isFresh(claim.tick(), player.getServer().getTicks())
                ? claim.targetIds() : null;
    }
}
