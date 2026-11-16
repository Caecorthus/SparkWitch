package dev.caecorthus.sparkwitch.client.bear.smoke;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeHole;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeClearS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeCloudRemoveS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeCloudS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeHoleS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client only: the BEAR smoke receivers, the only writers of {@link SmokeClientState}. Payloads are decoded on the
 * Netty thread (decoding never throws, a bad body arrives as null and is dropped here); Fabric 4.3.0 then runs every
 * play handler through {@code MinecraftClient#execute} ({@code ClientPlayNetworkAddon#receive}), and {@link #onClient}
 * still hops there itself, so the state is written on the client (render) thread only. The server decides who gets
 * smoke and clears it on round edges and world changes; a disconnect clears the state and frees the GPU volumes, and
 * so does the packet that takes the last cloud away ({@link #releaseIfEmpty}).
 * Client-side caps bound what a misbehaving server can make the renderer hold.
 * 仅客户端：BEAR 烟雾接收器，是 {@link SmokeClientState} 唯一的写入方。数据包在 Netty 线程解码（解码从不抛出，坏正文以
 * null 到达并在此丢弃）；Fabric 4.3.0 随后通过 {@code MinecraftClient#execute}（{@code ClientPlayNetworkAddon#receive}）
 * 执行每个 play 处理器，{@link #onClient} 仍会自行切到该线程，因此状态只在客户端（渲染）线程写入。由服务端决定谁收到烟雾，
 * 并在回合节点与换世界时清除；断线时清空状态并释放 GPU 浓度体，带走最后一团烟的数据包同样如此（{@link #releaseIfEmpty}）。
 * 客户端上限约束异常服务端能让渲染器持有的数量。
 */
public final class SmokeNetworkClient {
    /**
     * Server caps are 8 clouds, 32 holes and 16 tunnels per world. / 服务端上限为每世界 8 团烟、32 个空洞与 16 条通道。
     */
    static final int MAX_CLOUDS = 32;
    static final int MAX_HOLES = 64;
    /** Tunnels kept; past it the oldest goes, so the newest shot always shows. / 保留的通道数；超出时丢最早的。 */
    static final int MAX_TUNNELS = 32;

    private static boolean registered;
    private static boolean warnedMalformed;

    private SmokeNetworkClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SmokeCloudS2CPayload.ID, (payload, context) ->
                onClient(context.client(), () -> putCloud(payload.cloud())));
        ClientPlayNetworking.registerGlobalReceiver(SmokeCloudRemoveS2CPayload.ID, (payload, context) ->
                onClient(context.client(), () -> {
                    SmokeClientState.remove(payload.cloudId());
                    releaseIfEmpty();
                }));
        ClientPlayNetworking.registerGlobalReceiver(SmokeHoleS2CPayload.ID, (payload, context) ->
                onClient(context.client(), () -> addHole(payload.hole())));
        ClientPlayNetworking.registerGlobalReceiver(SmokeClearS2CPayload.ID, (payload, context) ->
                onClient(context.client(), () -> {
                    SmokeClientState.clear();
                    releaseIfEmpty();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SmokeNetworkClient::reset));
        // Also on JOIN: a proxy backend switch re-joins without a disconnect, and play state only arrives after JOIN.
        // JOIN 时同样重置：代理切换后端时不会断线而是重新加入，而 play 阶段的状态只会在 JOIN 之后到达。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(SmokeNetworkClient::reset));
    }

    /** Clears every cloud and hole and frees their textures (render thread). / 清空全部烟团与空洞并释放其纹理（渲染线程）。 */
    static void reset() {
        SmokeClientState.clear();
        SmokeVolumes.releaseAll();
    }

    /**
     * After a remove or clear packet (render thread): once no cloud is left every pass stops at
     * {@link SmokeClientState#isEmpty()} and never calls {@link SmokeVolumes#prepare} again, so the volumes of the last
     * cloud would otherwise stay (textures, upload buffers, and a stale {@link SmokeVolumes#opticalDepth}) until the next
     * cloud or the disconnect. / 收到移除或清空数据包之后（渲染线程）：一旦没有烟团，各步骤都会停在
     * {@link SmokeClientState#isEmpty()}，不再调用 {@link SmokeVolumes#prepare}，否则最后一团烟的浓度体（纹理、上传缓冲，以及
     * 过期的 {@link SmokeVolumes#opticalDepth}）会一直留到下一团烟出现或断线。
     */
    private static void releaseIfEmpty() {
        if (SmokeClientState.isEmpty()) {
            SmokeVolumes.releaseAll();
        }
    }

    private static void putCloud(SmokeCloudSnapshot cloud) {
        if (cloud == null) {
            warnMalformed("cloud");
            return;
        }
        boolean known = SmokeClientState.clouds().stream().anyMatch(existing -> existing.cloudId() == cloud.cloudId());
        if (known || SmokeClientState.clouds().size() < MAX_CLOUDS) {
            SmokeClientState.put(cloud);
        }
    }

    private static void addHole(SmokeHole hole) {
        if (hole == null) {
            warnMalformed("hole");
            return;
        }
        if (hole.isTunnel()) {
            SmokeClientState.addTunnel(hole, MAX_TUNNELS);
        } else if (SmokeClientState.holes().size() < MAX_HOLES) {
            SmokeClientState.addHole(hole);
        }
    }

    private static void warnMalformed(String what) {
        if (!warnedMalformed) {
            warnedMalformed = true;
            SparkWitch.LOGGER.warn("Dropped a malformed BEAR smoke {} payload (further ones are dropped silently).",
                    what);
        }
    }

    private static void onClient(MinecraftClient client, Runnable action) {
        if (client.isOnThread()) {
            action.run();
        } else {
            client.execute(action);
        }
    }
}
