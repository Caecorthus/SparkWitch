package dev.caecorthus.sparkwitch.util;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * The one way a SparkWitch module registers per-player cleanup for a disconnect. Each module still registers its own
 * cleanup at its own place, in its own order; this class only decides where and when that cleanup runs.
 * <p>Thread: Fabric fires {@code ServerPlayConnectionEvents.DISCONNECT} once per connection, usually from
 * {@code ClientConnection.channelInactive} on a Netty thread and otherwise from {@code handleDisconnection} on the
 * server thread. Cleanup edits inventories, components, entities and plain maps that the server tick uses, so it
 * always runs on the server thread: at once when the event already fired there, otherwise queued.
 * <p>Order, which the cleanups rely on and which their comments state ("only the session ends here; Wathe's deferred
 * ESCAPED kill then ..."):
 * <ul>
 * <li>Before Wathe's ESCAPED kill. Wathe's listener (default phase) queues that kill with {@code server.execute}; the
 * listeners here sit in {@link #PHASE}, ordered before the default phase, so their tasks are queued first (the
 * Dominator forced-shot and Schrödinger's Cat precedent). Several cleanups read "is this a living participant?" to
 * decide what a leaver keeps, and that answer is only right before the kill.</li>
 * <li>Before vanilla saves and removes the player. A queued task can lose that race when the disconnect lands in the
 * middle of a tick, and a kit stripped after the save comes back on the next login. So
 * {@code mixin/ServerPlayNetworkHandlerDisconnectCleanupMixin} calls {@link #runPending} at the head of
 * {@code onDisconnected}, which runs on the server thread before {@code PlayerManager.remove}.</li>
 * <li>Among SparkWitch's own cleanups: registration order, exactly as before.</li>
 * </ul>
 * Each task runs exactly once, whichever of the two server-thread callers reaches it first.
 * <p>SparkWitch 模块为断线注册逐玩家清理的唯一途径。各模块仍在自己的位置、按自己的顺序注册自己的清理；本类只决定清理在
 * 何处、何时运行。
 * <p>线程：Fabric 对每个连接只触发一次 {@code ServerPlayConnectionEvents.DISCONNECT}，通常来自 Netty 线程上的
 * {@code ClientConnection.channelInactive}，否则来自服务端线程上的 {@code handleDisconnection}。清理会修改服务端 tick
 * 正在使用的物品栏、组件、实体与普通映射，因此始终在服务端线程运行：事件本就在该线程触发时立即执行，否则排队。
 * <p>顺序（各清理依赖它，其注释也写明：“这里只结束会话；随后 Wathe 延迟执行的 ESCAPED 击杀……”）：
 * <ul>
 * <li>先于 Wathe 的 ESCAPED 击杀。Wathe 的监听器（默认阶段）用 {@code server.execute} 把该击杀排队；这里的监听器位于排在
 * 默认阶段之前的 {@link #PHASE}，因此其任务先入队（沿用支配者强制开枪与薛定谔的猫的先例）。若干清理通过“是否为存活参与者”
 * 来决定离开者保留什么，而这个答案只有在击杀之前才正确。</li>
 * <li>先于原版保存并移除玩家。断线落在一刻中途时，排队的任务可能输掉这场竞争，而保存之后才清除的装备会在下次登录时回来。
 * 因此 {@code mixin/ServerPlayNetworkHandlerDisconnectCleanupMixin} 在 {@code onDisconnected} 开头调用
 * {@link #runPending}，它在服务端线程上、先于 {@code PlayerManager.remove} 运行。</li>
 * <li>SparkWitch 自身各清理之间：保持注册顺序，与此前完全一致。</li>
 * </ul>
 * 每个任务恰好运行一次，由两个服务端线程调用方中先到达的那个执行。
 */
public final class ServerDisconnectCleanup {
    /** Ordered before {@link Event#DEFAULT_PHASE}, where Wathe queues its ESCAPED kill. / 排在 Wathe 排队 ESCAPED 击杀所在的默认阶段之前。 */
    public static final Identifier PHASE = SparkWitch.id("disconnect_cleanup");

    /**
     * Tasks queued from a Netty thread, per leaving player, until {@code onDisconnected} drains them. Written from Netty
     * and server threads, hence the concurrent types. / 从 Netty 线程排队、按离开的玩家保存的任务，直到
     * {@code onDisconnected} 将其取空。由 Netty 与服务端线程写入，因此使用并发类型。
     */
    private static final Map<ServerPlayerEntity, Queue<Pending>> PENDING = new ConcurrentHashMap<>();
    private static boolean wired;

    private ServerDisconnectCleanup() {
    }

    /** One module's cleanup for a leaving player; always called on the server thread. / 某模块对离开玩家的清理；始终在服务端线程调用。 */
    @FunctionalInterface
    public interface Cleanup {
        void run(ServerPlayerEntity player, MinecraftServer server);
    }

    public static synchronized void register(Cleanup cleanup) {
        if (!wired) {
            wired = true;
            ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
            // A server that stops never drains: drop what is left so no player entity is kept alive.
            // 停止的服务器不会再取空队列：丢弃剩余任务，避免玩家实体被一直引用。
            ServerLifecycleEvents.SERVER_STARTING.register(server -> PENDING.clear());
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
        }
        ServerPlayConnectionEvents.DISCONNECT.register(PHASE, (handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                onServerThread(server, player, () -> cleanup.run(player, server));
            }
        });
    }

    private static void onServerThread(MinecraftServer server, ServerPlayerEntity player, Runnable task) {
        if (server.isOnThread()) {
            // The event fired on the server thread: run in place, as execute() would. / 事件本就在服务端线程触发：就地执行，与 execute() 相同。
            task.run();
            return;
        }
        Pending pending = new Pending(task);
        PENDING.computeIfAbsent(player, key -> new ConcurrentLinkedQueue<>()).add(pending);
        server.execute(pending);
    }

    /**
     * Server thread, head of {@code ServerPlayNetworkHandler.onDisconnected}: runs, in order, every cleanup of this
     * player that the task queue has not reached yet, so all of them precede the save. A failing cleanup is logged and
     * never stops the others or vanilla's own disconnect handling.
     * 服务端线程、{@code ServerPlayNetworkHandler.onDisconnected} 开头：按顺序运行任务队列尚未执行到的该玩家的全部清理，
     * 使它们都先于保存。某个清理失败时只记录日志，绝不妨碍其余清理或原版自身的断线处理。
     */
    public static void runPending(ServerPlayerEntity player) {
        Queue<Pending> queue = player == null ? null : PENDING.remove(player);
        if (queue == null) {
            return;
        }
        for (Pending pending = queue.poll(); pending != null; pending = queue.poll()) {
            try {
                pending.run();
            } catch (RuntimeException failure) {
                SparkWitch.LOGGER.error("SparkWitch disconnect cleanup failed", failure);
            }
        }
    }

    /** Runs its task once, for whichever caller comes first. / 任务只运行一次，由先到的调用方执行。 */
    private static final class Pending implements Runnable {
        private final Runnable task;
        private final AtomicBoolean done = new AtomicBoolean();

        private Pending(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            if (done.compareAndSet(false, true)) {
                task.run();
            }
        }
    }
}
