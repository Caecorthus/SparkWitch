package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecBulletImpactsS2CPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client only. Owned by WP7: the {@code sparkwitch:usec_bullet_impacts} receiver and the visual-only block cracks
 * (D13). Called once from {@code UsecClientModule.register()}. Each impact feeds {@link UsecCrackTracker}, whose stage
 * changes go to vanilla {@code WorldRenderer#setBlockBreakingInfo} under reserved negative breaker ids. Verified against
 * the mapped 1.21.1 jar: an id holds one block (a new position moves the entry), a stage outside 0..9 removes it, the
 * highest stage per block is drawn, only within 32 blocks of the camera, and {@code WorldRenderer#tick} drops entries
 * not refreshed for 400 ticks but never clears them on a world change, so this class clears its own cracks on a world
 * change, a disconnect and a round start or end (Wathe {@code isRunning} flips). It never changes a block and never
 * sends a packet; the server decides who receives impacts.
 * 仅客户端。归 WP7 所有：{@code sparkwitch:usec_bullet_impacts} 接收器与纯视觉方块裂痕（D13）。由
 * {@code UsecClientModule.register()} 调用一次。每次命中交给 {@link UsecCrackTracker}，其级别变化以保留的负数破坏者 id 交给原版
 * {@code WorldRenderer#setBlockBreakingInfo}。已对照映射后的 1.21.1 jar 核实：一个 id 只对应一个方块（新位置会移动该条目），
 * 0..9 以外的级别会移除条目，每个方块绘制最高级别，且只在镜头 32 格内绘制；{@code WorldRenderer#tick} 会丢弃 400 刻未刷新的
 * 条目，但换世界时从不清除，因此本类在换世界、断线以及回合开始或结束（Wathe {@code isRunning} 变化）时自行清除裂痕。
 * 从不修改方块，也从不发送数据包；由服务端决定谁收到命中信息。
 */
public final class UsecImpactClient {
    private static final UsecCrackTracker TRACKER = new UsecCrackTracker();
    private static final UsecCrackTracker.Sink RENDERER = UsecImpactClient::setBreakingInfo;
    private static boolean registered;
    @Nullable
    private static ClientWorld trackedWorld;
    private static boolean trackedRoundRunning;

    private UsecImpactClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(UsecBulletImpactsS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> receive(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(UsecImpactClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(UsecImpactClient::reset));
    }

    static void receive(MinecraftClient client, UsecBulletImpactsS2CPacket packet) {
        if (client.world == null) {
            return;
        }
        syncLifecycle(client);
        for (UsecBulletImpactsS2CPacket.Impact impact : packet.impacts()) {
            TRACKER.hit(impact.pos().asLong(), impact.stage(), RENDERER);
        }
    }

    static void tick(MinecraftClient client) {
        syncLifecycle(client);
        if (client.world != null && !client.isPaused()) {
            TRACKER.tick(RENDERER);
        }
    }

    /**
     * Clears on a world change (a disconnect leaves no world) and whenever the Wathe round starts or ends.
     * 换世界（断线后没有世界）以及 Wathe 回合开始或结束时清除。
     */
    private static void syncLifecycle(MinecraftClient client) {
        ClientWorld world = client.world;
        boolean running = world != null && GameWorldComponent.KEY.get(world).isRunning();
        if (world != trackedWorld || running != trackedRoundRunning) {
            TRACKER.clear(RENDERER);
            trackedWorld = world;
            trackedRoundRunning = running;
        }
    }

    private static void reset() {
        TRACKER.clear(RENDERER);
        trackedWorld = null;
        trackedRoundRunning = false;
    }

    private static void setBreakingInfo(int breakerId, long packedPos, int stage) {
        WorldRenderer renderer = MinecraftClient.getInstance().worldRenderer;
        if (renderer != null) {
            renderer.setBlockBreakingInfo(breakerId, BlockPos.fromLong(packedPos), stage);
        }
    }
}
