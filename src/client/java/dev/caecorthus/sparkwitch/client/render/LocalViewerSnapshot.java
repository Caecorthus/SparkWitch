package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Per-world-pass memo of LOCAL-VIEWER facts: answers that depend only on the local player and client state, never on
 * the entity being drawn. Wathe asks {@code getInstinctHighlight} (and SparkWitch's {@code isInstinctEnabled*} gates,
 * outline vetoes and Blind gates) for every rendered entity every frame, and each hook re-derived the same viewer facts
 * (role, liveness, Wraith state, Perception, Curser confusion, Fear, Blind view, ...) several times per call.
 * <p>Contract: a fact is memoized only between {@code WorldRenderEvents.START} and {@code WorldRenderEvents.END} of one
 * world pass, on the render thread, while {@code client.player}, {@code client.world} and the confirmed-server flag are
 * the ones captured at START. Everywhere else (ticks, HUD, screens, packet handlers, the integrated server thread,
 * another viewer) every call recomputes, exactly as before. Inside the pass nothing a fact reads can change: packets
 * and {@code client.execute} tasks run in {@code MinecraftClient#render}'s task drain and client ticks
 * ({@code handleInputEvents}, CCA client ticks, world tick) both run before {@code GameRenderer#render}; GLFW input
 * callbacks run in {@code glfwPollEvents} at {@code swapBuffers}, after the frame. The pass is also closed at every
 * frame start ({@code MinecraftClient#render} HEAD, {@code WraithMinecraftClientMixin}), at START/END_CLIENT_TICK and
 * on disconnect, so even a skipped END can never carry a value across a frame, a task drain or a tick. Facts must stay
 * viewer-only and must never read the instinct key ({@code isPressed}/{@code isInstinctEnabled}): SparkStrength's
 * Blackout and the USEC cloak release it per target. Each {@link Fact} has exactly one owner rule.
 * 按世界渲染 pass 缓存的本地观察者事实：只取决于本地玩家与客户端状态、从不取决于正在绘制的实体的结果。Wathe 每帧对每个
 * 渲染实体调用 {@code getInstinctHighlight}（以及 SparkWitch 的 {@code isInstinctEnabled*} 门禁、描边否决与盲人闸门），
 * 每个钩子在一次调用中多次重复计算相同的观察者事实（职业、存活、冤魂状态、感知、诅咒混乱、恐惧、盲人视图等）。
 * <p>契约：事实只在一次世界渲染的 {@code WorldRenderEvents.START} 与 {@code WorldRenderEvents.END} 之间、在渲染线程上、
 * 且 {@code client.player}、{@code client.world} 与服务端确认标记仍为 START 时记录的值时被缓存。其他任何场合（tick、HUD、
 * 界面、数据包处理、集成服务端线程、其他观察者）每次调用都重新计算，与原先完全相同。pass 内事实读取的任何状态都不会变化：
 * 数据包与 {@code client.execute} 任务在 {@code MinecraftClient#render} 的任务队列中执行，客户端 tick（{@code handleInputEvents}、
 * CCA 客户端 tick、世界 tick）也都在 {@code GameRenderer#render} 之前运行；GLFW 输入回调在 {@code swapBuffers} 的
 * {@code glfwPollEvents} 中、即本帧之后运行。每帧开始（{@code MinecraftClient#render} HEAD，见
 * {@code WraithMinecraftClientMixin}）、START/END_CLIENT_TICK 与断开连接时也会关闭 pass，因此即使 END 被跳过，缓存值也不会
 * 跨越帧、任务队列或 tick。事实必须只与观察者有关，且绝不能读取本能键（{@code isPressed}/{@code isInstinctEnabled}）：
 * SparkStrength 熄灯与 USEC 隐蔽会按目标松开它。每个 {@link Fact} 只有一个所属规则。
 */
public final class LocalViewerSnapshot {
    /** One memo bit per fact, each with exactly one owner rule. / 每个事实一个缓存位，各自只有一个所属规则。 */
    public enum Fact {
        /** {@code GameFunctions.isPlayerPlayingAndAlive} (this class). */
        PLAYING_AND_ALIVE,
        /** {@code PlayerEntity.isSpectator} (this class). */
        SPECTATOR,
        /** {@code GameFunctions.isPlayerSpectatingOrCreative} (this class). */
        SPECTATING_OR_CREATIVE,
        /** {@code WraithClientState.isActive} (this class). */
        WRAITH_ACTIVE,
        /** {@code WraithClientState.isPromoted} (this class). */
        WRAITH_PROMOTED,
        /** {@code WraithViewerRules}: Wathe-dead spectator. */
        WRAITH_DEAD_SPECTATOR,
        /** {@code WraithViewerRules}: living killer viewer (promoted Saboteur reveal). */
        WRAITH_LIVING_KILLER_VIEWER,
        /** {@code WraithViewerRules}: witch-faction viewer (promoted Curser reveal). */
        WRAITH_WITCH_FACTION_VIEWER,
        /** {@code BlackRavenInstinctVeto.seesSpectatorInformation}. */
        BLACK_RAVEN_SEES_SPECTATOR_INFORMATION,
        /** {@code BlackRavenClientState.isEligible}. */
        BLACK_RAVEN_ELIGIBLE,
        /** {@code BlackRavenClientState.isPerceptionActive}. */
        BLACK_RAVEN_PERCEPTION_ACTIVE,
        /** {@code CurserClientHooks.isLocallyConfused}. */
        CURSER_LOCALLY_CONFUSED,
        /** {@code CurserClientHooks.canUseInstinct}. */
        CURSER_CAN_USE_INSTINCT,
        /** {@code ControlExpertStatusClient.blocksLocalInstinct}. */
        CONTROL_EXPERT_BLOCKS_INSTINCT,
        /** {@code GrandWitchFearClientHooks}: the local player is feared. */
        GRAND_WITCH_FEARED,
        /** {@code BlindView.isActive}. */
        BLIND_VIEW_ACTIVE;

        final int bit = 1 << ordinal();
    }

    private static final Pass PASS = new Pass();
    private static boolean registered;

    private LocalViewerSnapshot() {
    }

    /** Client init: wires the pass lifecycle. / 客户端初始化：接入 pass 生命周期。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.START.register(context -> beginWorldPass());
        WorldRenderEvents.END.register(context -> close());
        ClientTickEvents.START_CLIENT_TICK.register(client -> close());
        ClientTickEvents.END_CLIENT_TICK.register(client -> close());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
    }

    /** Render thread, world pass start: forget everything and start memoizing. / 渲染线程，世界 pass 开始：清空并开始缓存。 */
    public static void beginWorldPass() {
        MinecraftClient client = MinecraftClient.getInstance();
        PASS.begin(client.player, client.world, SparkWitchServerConnection.isConfirmedServer(), Thread.currentThread());
    }

    /** Ends memoization until the next world pass starts. / 结束缓存，直到下一次世界 pass 开始。 */
    public static void close() {
        PASS.close();
    }

    /**
     * A fact about {@code MinecraftClient.getInstance().player} whose rule reads the local player itself; memoized only
     * inside the pass. / 规则自行读取本地玩家的事实；仅在 pass 内缓存。
     */
    public static boolean localFact(Fact fact, BooleanSupplier rule) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !PASS.usable(client.player, client.world, SparkWitchServerConnection.isConfirmedServer(),
                Thread.currentThread())) {
            return rule.getAsBoolean();
        }
        return PASS.memo(fact.bit, rule);
    }

    /**
     * {@code rule.test(viewer)}, memoized only when {@code viewer} is the local player inside the pass; any other viewer
     * is answered fresh. / 仅当 {@code viewer} 是 pass 内的本地玩家时缓存 {@code rule.test(viewer)}；其他观察者实时计算。
     */
    public static boolean viewerFact(Fact fact, @Nullable PlayerEntity viewer, Predicate<PlayerEntity> rule) {
        if (!isPassViewer(viewer)) {
            return rule.test(viewer);
        }
        return PASS.memo(fact.bit, rule, viewer);
    }

    /** True when {@code viewer} is the local player of the open pass. / {@code viewer} 是当前 pass 的本地玩家时为 true。 */
    public static boolean isPassViewer(@Nullable PlayerEntity viewer) {
        if (viewer == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && viewer == client.player
                && PASS.usable(client.player, client.world, SparkWitchServerConnection.isConfirmedServer(),
                Thread.currentThread());
    }

    /** The viewer's raw Wathe role ({@code getRole}). / 观察者的 Wathe 原始职业（{@code getRole}）。 */
    public static @Nullable Role role(PlayerEntity viewer) {
        if (!isPassViewer(viewer)) {
            return lookupRole(viewer);
        }
        return (Role) PASS.memoRole(LocalViewerSnapshot::lookupRole, viewer);
    }

    public static boolean playingAndAlive(@Nullable PlayerEntity viewer) {
        return viewerFact(Fact.PLAYING_AND_ALIVE, viewer, GameFunctions::isPlayerPlayingAndAlive);
    }

    public static boolean spectator(PlayerEntity viewer) {
        return viewerFact(Fact.SPECTATOR, viewer, PlayerEntity::isSpectator);
    }

    public static boolean spectatingOrCreative(@Nullable PlayerEntity viewer) {
        return viewerFact(Fact.SPECTATING_OR_CREATIVE, viewer, GameFunctions::isPlayerSpectatingOrCreative);
    }

    public static boolean wraithActive(@Nullable PlayerEntity viewer) {
        return viewerFact(Fact.WRAITH_ACTIVE, viewer, WraithClientState::isActive);
    }

    public static boolean wraithPromoted(@Nullable PlayerEntity viewer) {
        return viewerFact(Fact.WRAITH_PROMOTED, viewer, WraithClientState::isPromoted);
    }

    private static @Nullable Role lookupRole(PlayerEntity viewer) {
        return GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
    }

    /**
     * The pass state, free of Minecraft types so it is unit-testable. A value is stored only if the pass that asked
     * is still the open one after the rule returned; a rule that throws stores nothing.
     * pass 状态，不含 Minecraft 类型以便单元测试。只有规则返回后提问的 pass 仍然打开时才存值；规则抛出异常时不存。
     */
    static final class Pass {
        private boolean open;
        private long generation;
        private @Nullable Object player;
        private @Nullable Object world;
        private boolean confirmed;
        private @Nullable Thread thread;
        private int known;
        private int values;
        private boolean roleKnown;
        private @Nullable Object role;

        void begin(@Nullable Object player, @Nullable Object world, boolean confirmed, Thread thread) {
            reset();
            this.player = player;
            this.world = world;
            this.confirmed = confirmed;
            this.thread = thread;
            // Only an in-world pass memoizes. / 只有世界内的 pass 才缓存。
            open = player != null && world != null;
        }

        void close() {
            reset();
        }

        private void reset() {
            generation++;
            open = false;
            player = null;
            world = null;
            thread = null;
            known = 0;
            values = 0;
            roleKnown = false;
            role = null;
        }

        boolean usable(@Nullable Object player, @Nullable Object world, boolean confirmed, Thread thread) {
            return open && this.thread == thread && this.player == player && this.world == world
                    && this.confirmed == confirmed;
        }

        boolean memo(int bit, BooleanSupplier rule) {
            if ((known & bit) != 0) {
                return (values & bit) != 0;
            }
            long asked = generation;
            boolean value = rule.getAsBoolean();
            store(asked, bit, value);
            return value;
        }

        <T> boolean memo(int bit, Predicate<? super T> rule, T argument) {
            if ((known & bit) != 0) {
                return (values & bit) != 0;
            }
            long asked = generation;
            boolean value = rule.test(argument);
            store(asked, bit, value);
            return value;
        }

        <T> @Nullable Object memoRole(Function<? super T, ?> lookup, T argument) {
            if (roleKnown) {
                return role;
            }
            long asked = generation;
            Object value = lookup.apply(argument);
            if (asked == generation && open) {
                role = value;
                roleKnown = true;
            }
            return value;
        }

        private void store(long asked, int bit, boolean value) {
            if (asked != generation || !open) {
                return;
            }
            known |= bit;
            if (value) {
                values |= bit;
            }
        }
    }
}
