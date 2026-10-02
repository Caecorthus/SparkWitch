package dev.caecorthus.sparkwitch.client.riftwalker.session;

import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftExitC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftHopC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the "inside a Rift Gate" session (plan §6.3–6.5, D10/D11, C2). Presentation and requests only: it
 * reads the owner-synced {@link RiftSessionComponent} and never predicts entry or exit (every edge comes from a sync),
 * and it sends {@code sparkwitch:rift_hop} / {@code sparkwitch:rift_exit}, which the server re-validates.
 * <p>
 * Runs on START_CLIENT_TICK, before {@code handleInputEvents}. Entry edge: clear (never release) the item in use, stop
 * mining, close gameplay screens, drain queued presses, untoggle sticky keys, reset the shared ability-key bridge,
 * install {@link RiftFrozenInput} and zero the body's speeds and sprint; Shift and the hop keys must be released before
 * they count. While inside: keys outside {@link RiftSessionInputRules#ALLOWED_KEYS} read as released for the rest of
 * the game ({@code RiftSessionKeyBindingMixin}), while this controller reads Shift, A/D, the use key and keys 1/2 raw;
 * scroll or 1/2 select ⬅️/➡️, the use key fires the selected slot, A/D fire directly (one step per fresh press, spaced
 * {@link RiftSessionInputRules#HOP_SEND_SPACING_TICKS}); a fresh Shift sends the exit request. Exit edge (server says
 * outside, or the local player object was replaced): restore a fresh keyboard input, untoggle sticky keys, drain queued
 * presses. Disconnect / join force the local state off and reset the grey filter.
 * 「在裂隙门内」会话的客户端（plan §6.3–6.5、D10/D11、C2）。只负责表现与请求：读取仅同步给拥有者的 {@link RiftSessionComponent}，
 * 从不预测进门或出门（每个边沿都来自同步），并发送由服务端重新校验的 {@code sparkwitch:rift_hop} / {@code sparkwitch:rift_exit}。
 * 运行于 START_CLIENT_TICK，早于 {@code handleInputEvents}。进门边沿：清除（绝不释放）正在使用的物品、停止挖掘、关闭游戏界面、
 * 清空积压按键、取消切换式按键、重置共享技能键桥、装上 {@link RiftFrozenInput} 并清零本体速度与疾跑；Shift 与跳门键必须先松开才算数。
 * 门内：白名单之外的按键对游戏其余部分视为未按下（{@code RiftSessionKeyBindingMixin}），本控制器则直接读取 Shift、A/D、使用键与
 * 1/2 键的原始状态；滚轮或 1/2 选择 ⬅️/➡️，使用键触发选中格，A/D 直接触发（每次新按下只跳一步，间隔
 * {@link RiftSessionInputRules#HOP_SEND_SPACING_TICKS}）；新按下的 Shift 发送出门请求。出门边沿（服务端报告已出门，或本地玩家对象被替换）：
 * 换回新的键盘输入、取消切换式按键、清空积压按键。断线/加入时强制关闭本地状态并重置灰度滤镜。
 */
public final class RiftSessionClient {
    private static boolean initialized;
    /** Read by the input and render mixins. / 由输入与渲染 mixin 读取。 */
    private static volatile boolean active;
    @Nullable
    private static ClientPlayerEntity boundPlayer;
    private static int activeSessionId;
    private static volatile int selectedSlot = RiftSessionInputRules.DEFAULT_SLOT;
    private static boolean exitArmed;
    private static boolean previousWasDown = true;
    private static boolean nextWasDown = true;
    private static boolean useWasDown = true;
    private static long clientTicks;
    private static long lastHopTick = RiftSessionInputRules.NEVER;
    private static long lastExitTick = RiftSessionInputRules.NEVER;
    private static double scrollHorizontal;
    private static double scrollVertical;

    private RiftSessionClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // START_CLIENT_TICK runs before handleInputEvents in the same tick. / START_CLIENT_TICK 在同一刻的 handleInputEvents 之前运行。
        ClientTickEvents.START_CLIENT_TICK.register(RiftSessionClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forceExit());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            forceExit();
            RiftGrayscaleFilter.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RiftGrayscaleFilter.reset());
        RiftGrayscaleFilter.register();
    }

    /** True while the local player is inside a gate (server-synced edge). / 本地玩家在门内时为 true（服务端同步的边沿）。 */
    public static boolean isActive() {
        return active;
    }

    /** False for every key outside the in-gate allowlist. / 门内白名单之外的按键返回 false。 */
    public static boolean allowsKey(KeyBinding key) {
        return RiftSessionInputRules.isAllowedKey(key.getTranslationKey(), key.getCategory());
    }

    /**
     * Snapshot for the HUD, or null when not inside. / 供 HUD 使用的快照，不在门内时为 null。
     */
    @Nullable
    public static View view() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!active || player == null) {
            return null;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        if (session == null || !session.inside()) {
            return null;
        }
        return new View(session.gateNumber(), session.ringIndex(), session.ringSize(), session.stayRemainingTicks(),
                session.stayLimitTicks(), selectedSlot);
    }

    /**
     * Disconnect or join: end locally without sending anything. Off-thread callers are rescheduled.
     * 断线或加入：本地结束且不发送任何数据包。非主线程的调用会被重新调度到主线程。
     */
    public static void forceExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (!client.isOnThread()) {
            client.execute(RiftSessionClient::forceExit);
            return;
        }
        if (active) {
            end(client);
        }
    }

    /**
     * {@code handleInputEvents} HEAD backup: clear the item in use without a release and stop mining.
     * {@code handleInputEvents} HEAD 兜底：清除正在使用的物品（不触发松手）并停止挖掘。
     */
    public static void settleInput(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && player.isUsingItem()) {
            player.clearActiveItem();
        }
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    /**
     * In-game scroll while inside (called from {@code RiftSessionMouseMixin}, which cancels vanilla's spectator menu
     * cycling and fly-speed change): moves the ⬅️/➡️ selection.
     * 门内的游戏内滚轮（由 {@code RiftSessionMouseMixin} 调用，它取消了原版旁观者菜单切换与飞行速度调整）：移动 ⬅️/➡️ 选择。
     */
    public static void onScroll(MinecraftClient client, double horizontal, double vertical) {
        if (!active) {
            return;
        }
        RiftSessionInputRules.Scroll scroll = RiftSessionInputRules.accumulate(scrollHorizontal, scrollVertical,
                horizontal, vertical, client.options.getDiscreteMouseScroll().getValue(),
                client.options.getMouseWheelSensitivity().getValue());
        scrollHorizontal = scroll.horizontal();
        scrollVertical = scroll.vertical();
        if (scroll.steps() != 0) {
            selectedSlot = RiftSessionInputRules.scrollSlot(selectedSlot, scroll.steps());
        }
    }

    private static void tick(MinecraftClient client) {
        clientTicks++;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            if (active) {
                end(client);
            }
            return;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        boolean inside = session != null && session.inside() && session.sessionId() != 0;
        int sessionId = inside ? session.sessionId() : 0;
        switch (RiftSessionInputRules.transition(active, player != boundPlayer, inside, sessionId != activeSessionId)) {
            case START -> start(client, player, sessionId);
            case END -> end(client);
            case RESTART -> {
                end(client);
                start(client, player, sessionId);
            }
            case NONE -> {
            }
        }
        if (active) {
            maintain(player);
            handleInput(client, client.options);
        }
    }

    /**
     * The physical state of a binding's key from GLFW (keyboard key or mouse button), independent of the binding's
     * held flag that sticky-key untoggling clears. An unbound or scancode-only key reads as up, so the flag decides.
     * 从 GLFW 读取按键绑定对应物理按键（键盘键或鼠标键）的状态，不受取消切换式按键时被清除的按住标记影响。未绑定或仅有扫描码的
     * 按键视为松开，由标记决定。
     */
    private static boolean physicallyHeld(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key == null || key.equals(InputUtil.UNKNOWN_KEY) || key.getCode() < 0 || client.getWindow() == null) {
            return false;
        }
        long window = client.getWindow().getHandle();
        return switch (key.getCategory()) {
            case KEYSYM -> InputUtil.isKeyPressed(window, key.getCode());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    private static void start(MinecraftClient client, ClientPlayerEntity player, int sessionId) {
        // Clear, never release: releasing a Wathe knife fires its stab. / 只清除、绝不释放：释放 Wathe 的刀会触发刺击。
        settleInput(client);
        closeGameplayScreen(client);
        drainQueuedPresses(client);
        // Toggle sneak/sprint would otherwise stay "held" through the session. / 否则切换式潜行/疾跑会在整个会话中保持「按住」。
        KeyBinding.untoggleStickyKeys();
        WitchAbilityKeyBridge.reset();
        player.input = new RiftFrozenInput();
        player.forwardSpeed = 0.0F;
        player.sidewaysSpeed = 0.0F;
        player.upwardSpeed = 0.0F;
        player.setJumping(false);
        player.setSprinting(false);
        Vec3d velocity = player.getVelocity();
        player.setVelocity(0.0, velocity.y, 0.0);

        boundPlayer = player;
        activeSessionId = sessionId;
        selectedSlot = RiftSessionInputRules.DEFAULT_SLOT;
        // Every key counts as held at the edge, so a key held while entering must be released first.
        // 边沿处所有按键都视为按住，因此进门时一直按住的键必须先松开。
        exitArmed = false;
        previousWasDown = true;
        nextWasDown = true;
        useWasDown = true;
        lastHopTick = RiftSessionInputRules.NEVER;
        lastExitTick = RiftSessionInputRules.NEVER;
        scrollHorizontal = 0.0;
        scrollVertical = 0.0;
        active = true;
    }

    private static void maintain(ClientPlayerEntity player) {
        if (!(player.input instanceof RiftFrozenInput)) {
            player.input = new RiftFrozenInput();
        }
        player.setSprinting(false);
    }

    private static void handleInput(MinecraftClient client, GameOptions options) {
        // Shift: armed once seen released (flag AND physical key, B-5) after the edge; then every queued press asks to
        // leave. Presses queued while unarmed (OS key repeat of a Shift held through entry) are taken and dropped.
        // Shift：边沿后看到松开一次（标记与物理按键都松开，B-5）即上膛；之后每次积压的按下都请求出门。未上膛期间积压的按下
        // （进门时一直按住的 Shift 的系统按键重复）被取走并丢弃。
        KeyAccess sneak = KeyAccess.of(options.sneakKey);
        int sneakPresses = sneak.take();
        boolean sneakDown = sneak.down();
        if (RiftSessionInputRules.exitRequested(exitArmed, sneakPresses)) {
            requestExit();
        }
        exitArmed = RiftSessionInputRules.armAfter(exitArmed, sneakDown,
                !exitArmed && physicallyHeld(client, options.sneakKey));

        KeyAccess previous = KeyAccess.of(options.leftKey);
        KeyAccess next = KeyAccess.of(options.rightKey);
        KeyAccess use = KeyAccess.of(options.useKey);
        boolean previousQueued = previous.take() > 0;
        boolean nextQueued = next.take() > 0;
        boolean useQueued = use.take() > 0;
        boolean previousDown = previous.down();
        boolean nextDown = next.down();
        boolean useDown = use.down();
        boolean previousFresh = RiftSessionInputRules.freshPress(previousWasDown, previousDown, previousQueued);
        boolean nextFresh = RiftSessionInputRules.freshPress(nextWasDown, nextDown, nextQueued);
        boolean useFresh = RiftSessionInputRules.freshPress(useWasDown, useDown, useQueued);
        previousWasDown = previousDown;
        nextWasDown = nextDown;
        useWasDown = useDown;

        boolean firstQueued = KeyAccess.of(options.hotbarKeys[0]).take() > 0;
        boolean secondQueued = KeyAccess.of(options.hotbarKeys[1]).take() > 0;
        selectedSlot = RiftSessionInputRules.hotbarSelection(selectedSlot, firstQueued, secondQueued);

        int direction = RiftSessionInputRules.hopDirection(previousFresh, nextFresh, useFresh, selectedSlot);
        if (direction != 0) {
            if (previousFresh || nextFresh) {
                // A/D also move the highlight, so the bar shows what fired. / A/D 同时移动高亮，栏上显示触发的格子。
                selectedSlot = RiftSessionInputRules.slotFor(direction);
            }
            requestHop(direction);
        }
    }

    private static void requestHop(int direction) {
        if (!RiftSessionInputRules.elapsed(clientTicks, lastHopTick, RiftSessionInputRules.HOP_SEND_SPACING_TICKS)
                || !ClientPlayNetworking.canSend(RiftHopC2SPacket.ID)) {
            return;
        }
        lastHopTick = clientTicks;
        ClientPlayNetworking.send(new RiftHopC2SPacket(activeSessionId, (byte) direction));
    }

    private static void requestExit() {
        if (!RiftSessionInputRules.elapsed(clientTicks, lastExitTick, RiftSessionInputRules.EXIT_RESEND_TICKS)
                || !ClientPlayNetworking.canSend(RiftExitC2SPacket.ID)) {
            return;
        }
        lastExitTick = clientTicks;
        // The session stays open locally until the server syncs the exit (it may refuse: no safe cell).
        // 在服务端同步出门之前本地会话保持打开（服务端可能拒绝：没有安全落点）。
        ClientPlayNetworking.send(new RiftExitC2SPacket(activeSessionId));
    }

    private static void end(MinecraftClient client) {
        ClientPlayerEntity bound = boundPlayer;
        active = false;
        if (bound != null && bound.input instanceof RiftFrozenInput) {
            bound.input = new KeyboardInput(client.options);
        }
        // setPressed(false) is a no-op on toggle keys; untoggle clears toggle sneak and sprint.
        // 切换式按键上 setPressed(false) 不起作用；untoggle 才能清除切换式潜行与疾跑。
        KeyBinding.untoggleStickyKeys();
        // Presses queued while input was locked must not replay. / 锁定期间积压的按键不得重放。
        drainQueuedPresses(client);
        // Right-click is the in-gate hop button: a forced exit while it is held must not use the held item next tick
        // (a Wathe revolver fires on the client at once); the player clicks again. Attack likewise.
        // 右键是门内的跳门键：按住右键时被强制出门，下一刻不得使用手中物品（Wathe 左轮会在客户端立即开火）；需重新点击。攻击键同理。
        if (client.options != null) {
            client.options.useKey.setPressed(false);
            client.options.attackKey.setPressed(false);
        }
        boundPlayer = null;
        activeSessionId = 0;
        selectedSlot = RiftSessionInputRules.DEFAULT_SLOT;
        exitArmed = false;
        scrollHorizontal = 0.0;
        scrollVertical = 0.0;
    }

    /**
     * Pause and option screens stay; gameplay screens close. Wathe's inventory and shop are
     * {@link ScreenHandlerProvider}s but not vanilla handled screens, so they are matched by that interface.
     * 暂停与设置界面保留；游戏界面关闭。Wathe 的背包与商店实现 {@link ScreenHandlerProvider}，因此按该接口判断。
     */
    private static void closeGameplayScreen(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen == null || screen instanceof GameMenuScreen || screen instanceof OptionsScreen
                || screen instanceof GameOptionsScreen) {
            return;
        }
        if (screen instanceof ScreenHandlerProvider<?> && client.player != null) {
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }
    }

    private static void drainQueuedPresses(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        for (KeyBinding key : client.options.allKeys) {
            if (!RiftSessionInputRules.VOICE_CHAT_CATEGORY.equals(key.getCategory())) {
                ((RiftSessionKeyAccess) key).sparkwitch$riftDrainPresses();
            }
        }
    }

    /** HUD snapshot. / HUD 快照。 */
    public record View(int gateNumber, int ringIndex, int ringSize, int stayRemainingTicks, int stayLimitTicks,
                       int selectedSlot) {
    }

    /** Raw key access through the duck interface. / 通过鸭子接口的原始按键读取。 */
    private record KeyAccess(RiftSessionKeyAccess key) {
        static KeyAccess of(KeyBinding binding) {
            return new KeyAccess((RiftSessionKeyAccess) binding);
        }

        int take() {
            return key.sparkwitch$riftTakePresses();
        }

        boolean down() {
            return key.sparkwitch$riftRawPressed();
        }
    }
}
