package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarMovement;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteCloseC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Client remote-view controller ("possession", owner decision Q2). It runs on START_CLIENT_TICK, before
 * {@code handleInputEvents} (a CCA client tick would be too late), and only reacts to the owner-only
 * {@code sparkwitch:seeker_status} component: the server alone opens sessions, the client only predicts the Shift
 * exit. It calls only {@code MinecraftClient#setCameraEntity}, never the server-side camera, and hands the camera
 * back only while it is still our focus, so a server camera writer (Taotie, Last Stand, Depression) always wins.
 * <p>
 * Start edge (mode not NONE, new session id): clear (never release) the item in use, stop mining, close gameplay
 * screens, drain queued presses, reset the shared ability-key bridge, install {@link SeekerFrozenInput}, zero the
 * body's speeds and sprint, save and disable chunk culling, hide the hand, then switch the camera once the focus
 * resolves. Every tick: exit locally when the player object changed, the server mode is NONE, the camera is no longer
 * ours, or the focus did not arrive within the connecting grace; Shift sends close and exits. End edge: restore the
 * camera only if still ours, a fresh keyboard input, {@code KeyBinding.untoggleStickyKeys()} (toggle sneak/sprint),
 * drain queued presses, restore culling and the hand, then let {@link SeekerBodyHold} settle the body.
 * <p>
 * Connecting (unlimited range, owner decision 2026-10-04): the server streams a far device's chunks and starts
 * tracking it only after it opened the session, so the focus entity can arrive late, an atomic switch can name a
 * device this client does not have yet, and a re-tracked device comes back as a new instance with the same id. The
 * focus is therefore re-resolved by synced id every tick ({@link SeekerRemoteViewRules#linkStep}). While it is missing
 * the session stays locked and keeps the last view (the body at session start), the CCTV overlay shows CONNECTING,
 * nothing is driven, looked at or cycled, and only after {@link SeekerRules#ATTACH_TIMEOUT_TICKS} (shared with the
 * server's CAR attach deadline) does the client give up with "Signal lost" and send close. A switch never ends the
 * view, so it can never report a spurious FOCUS_LOST. The server force-tracks the focus before its terrain arrives, so
 * after a bind CONNECTING stays until the focus's surrounding chunks are here
 * ({@link SeekerRemoteViewRules#revealsView}).
 * <p>
 * 客户端遥控视角控制器（“附身”，所有者决定 Q2）。运行于 START_CLIENT_TICK，早于 {@code handleInputEvents}
 * （CCA 客户端 tick 太晚），且只响应仅同步给拥有者的 {@code sparkwitch:seeker_status} 组件：只有服务端能打开会话，
 * 客户端只预测 Shift 退出。它只调用 {@code MinecraftClient#setCameraEntity}，从不使用服务端相机；
 * 只有相机仍是我们的焦点时才归还，因此服务端相机写入方（饕餮、最后一搏、抑郁）总是优先。
 * 开始沿（模式非 NONE、会话 id 变化）：清除（绝不释放）正在使用的物品、停止挖掘、关闭游戏界面、清空积压按键、重置共享技能键桥、
 * 装上 {@link SeekerFrozenInput}、清零本体速度与疾跑、保存并关闭区块剔除、隐藏手部，焦点可解析后再切换相机。每刻：玩家对象
 * 变化、服务端模式为 NONE、相机不再属于我们、或焦点未在连接宽限内到达时本地退出；Shift 发送关闭并退出。结束沿：仅当相机仍属于
 * 我们时归还；换回新的键盘输入；调用 {@code KeyBinding.untoggleStickyKeys()}（切换式潜行/疾跑）；清空积压按键；恢复区块剔除与
 * 手部渲染，然后交由 {@link SeekerBodyHold} 让本体稳定。
 * 连接中（无限距离，所有者决定 2026-10-04）：服务端在打开会话之后才会推送远处设备的区块并开始追踪它，因此焦点实体可能晚到，
 * 原子切换可能指向本客户端尚未拥有的设备，重新追踪的设备会以同 id 的新实例出现。因此每刻按同步的 id 重新解析焦点
 * （{@link SeekerRemoteViewRules#linkStep}）。焦点缺失时会话保持锁定并保留上一画面（会话开始时为本体），CCTV 叠加层显示
 * “正在连接”，不驾驶、不上报视角、不切换摄像头；只有在 {@link SeekerRules#ATTACH_TIMEOUT_TICKS}（与服务端 CAR 挂接截止共用）
 * 之后，客户端才以“信号丢失”放弃并发送关闭。切换从不结束视角，因此绝不会误报 FOCUS_LOST。服务端会在地形到达之前强制追踪焦点，
 * 因此绑定之后“正在连接”保持到焦点周围区块到达为止（{@link SeekerRemoteViewRules#revealsView}）。
 */
public final class SeekerRemoteViewClient {
    private static boolean registered;
    /** Read by the input mixins on the render thread. / 由输入 mixin 在渲染线程读取。 */
    private static volatile boolean active;
    private static SeekerSessionMode activeMode = SeekerSessionMode.NONE;
    private static int activeSessionId;
    @Nullable
    private static volatile Entity focus;
    @Nullable
    private static SeekerDeviceEntity device;
    @Nullable
    private static volatile ClientPlayerEntity boundPlayer;
    private static boolean savedChunkCulling = true;
    /**
     * The focus is bound and current; false while connecting (also read by the render-thread HUD and grid hooks).
     * 焦点已绑定且有效；连接中为 false（渲染线程的 HUD 与网格钩子也会读取）。
     */
    private static volatile boolean linked;
    /** Ticks waited for the focus since it was last bound or the session (re)started. / 自上次绑定或会话（重新）开始以来等待焦点的刻数。 */
    private static int waitedTicks;
    // The session and device id of the last bind, so a re-tracked car keeps its move sequence.
    // 上次绑定时的会话与设备 id，使重新追踪的小车沿用其移动序号。
    private static int boundSessionId;
    private static int boundDeviceId = -1;
    /**
     * The bound focus's terrain is on this client (or the grace ran out); until then CONNECTING stays up.
     * 已绑定焦点的地形已到达本客户端（或宽限已耗尽）；在此之前“正在连接”保持显示。
     */
    private static volatile boolean revealed;
    private static int revealTicks;

    // A session this player already started, left or gave up on is never re-entered (until the server says NONE).
    // 本玩家已进入、已离开或已放弃的会话不会再次进入（直到服务端报告 NONE）。
    @Nullable
    private static ClientPlayerEntity handledPlayer;
    private static int handledSessionId;

    private SeekerRemoteViewClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SeekerCarMovement.installClientDriveHook(SeekerCarClientDriver::isLocallyDriven);
        // START_CLIENT_TICK runs before handleInputEvents in the same tick. / START_CLIENT_TICK 在同一刻的 handleInputEvents 之前运行。
        ClientTickEvents.START_CLIENT_TICK.register(SeekerRemoteViewClient::tick);
        SeekerCarUseClient.register();
        SeekerBodyHold.register();
        // END_CLIENT_TICK runs after the world tick reset the car's prev* fields. / END_CLIENT_TICK 在世界 tick 重置小车 prev* 之后运行。
        ClientTickEvents.END_CLIENT_TICK.register(SeekerCarClientDriver::endTick);
    }

    /** True while the local player is possessing a device. / 本地玩家正在附身设备时为 true。 */
    public static boolean isActive() {
        return active;
    }

    public static SeekerSessionMode mode() {
        return active ? activeMode : SeekerSessionMode.NONE;
    }

    /**
     * The session is held but its focus is not on this client yet (or briefly missing); the CCTV overlay shows
     * CONNECTING. / 会话已保持但其焦点尚未到达本客户端（或短暂缺失）；CCTV 叠加层显示“正在连接”。
     */
    public static boolean isConnecting() {
        return active && !(linked && revealed);
    }

    /**
     * {@code SeekerRemoteTerrainGridMixin} hook: the render-grid centre axis (0/1/2 = x/y/z) is the camera position
     * while the camera looks through our focus, otherwise vanilla's player position.
     * {@code SeekerRemoteTerrainGridMixin} 钩子：镜头通过我们的焦点观看时，渲染网格中心坐标（0/1/2 = x/y/z）取镜头位置，
     * 否则保持原版的玩家位置。
     */
    public static double renderGridCenter(double original, int axis) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity current = focus;
        if (!active || current == null || client.getCameraEntity() != current) {
            return original;
        }
        Vec3d position = client.gameRenderer.getCamera().getPos();
        return axis == 0 ? position.x : axis == 1 ? position.y : position.z;
    }

    /** The render camera while active: the car itself, or the camera viewpoint. / 激活时的渲染相机：小车本身或摄像头视点。 */
    @Nullable
    public static Entity focus() {
        return active ? focus : null;
    }

    /**
     * Disconnect, join, respawn, world change: end without sending anything. Off-thread callers are rescheduled.
     * 断线、加入、重生、切换世界：结束且不发送任何数据包。非主线程的调用会被重新调度到主线程。
     */
    public static void forceExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (!client.isOnThread()) {
            client.execute(SeekerRemoteViewClient::forceExit);
            return;
        }
        if (active) {
            end(client, true);
        }
        SeekerBodyHold.reset();
        handledPlayer = null;
    }

    public static boolean isDriving(Entity entity) {
        return active && linked && activeMode == SeekerSessionMode.CAR && entity != null && entity == focus;
    }

    /** @return true when the look input was consumed by the remote focus. / 视角输入被遥控焦点消费时返回 true。 */
    public static boolean onLook(double deltaX, double deltaY) {
        if (!active) {
            return false;
        }
        Entity current = linked ? focus : null;
        if (current instanceof SeekerCameraViewpoint viewpoint) {
            viewpoint.look(deltaX, deltaY);
        } else if (current instanceof SeekerCarEntity car) {
            SeekerCarClientDriver.look(car, deltaX, deltaY);
        }
        // The body never turns while possessing, nor while connecting. / 附身期间与连接中本体都从不转向。
        return true;
    }

    /**
     * The real local body while possessing; its {@code isCamera()} stays true so it keeps sending movement packets
     * and never drifts.
     * 附身期间的真实本体；其 {@code isCamera()} 保持为 true，从而照常发送移动包，不会漂移。
     */
    public static boolean isPossessingBody(Object player) {
        return active && player != null && player == boundPlayer;
    }

    /** False for every key outside the remote-view allowlist. / 遥控视角白名单之外的按键返回 false。 */
    public static boolean allowsKey(KeyBinding key) {
        return SeekerRemoteViewRules.isAllowedKey(key.getTranslationKey(), key.getCategory());
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
     * {@code onSetCameraEntity} after the main-thread hop: a server camera writer takes over, so the view ends
     * without restoring the camera, and the server is told to close the session. When the packet's entity is not
     * loaded on this client ({@code next == null}), vanilla ignores the packet and would leave the render camera on
     * our device forever, so the camera is handed back to the body instead (still only while it is ours).
     * 在切到主线程之后的 {@code onSetCameraEntity}：服务端相机写入方接管，视角结束且不归还相机，并通知服务端关闭会话。
     * 若数据包指向的实体在本客户端尚未加载（{@code next == null}），原版会忽略该包，渲染相机将永远停留在我们的设备上，
     * 因此此时改为把相机归还本体（仍仅当相机属于我们时）。
     */
    public static void yieldCamera(@Nullable Entity next) {
        if (!active) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int session = activeSessionId;
        ClientPlayerEntity player = boundPlayer;
        end(client, next == null);
        sendClose(session);
        markHandled(player, session);
    }

    private static void tick(MinecraftClient client) {
        SeekerBodyHold.tick(client);
        ClientPlayerEntity player = client.player;
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null || client.world == null) {
            if (active) {
                end(client, true);
            }
            return;
        }
        SeekerSessionMode mode = SeekerClientState.sessionMode();
        int sessionId = SeekerClientState.sessionId();
        if (active && !continueSession(client, player, mode, sessionId)) {
            return;
        }
        if (!active) {
            tryStart(client, player, mode, sessionId);
        }
    }

    /** @return false when this tick is finished. / 本刻处理完毕时返回 false。 */
    private static boolean continueSession(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode mode,
                                           int sessionId) {
        boolean sessionChanged = mode != activeMode || sessionId != activeSessionId;
        // Connecting at session start, the camera is still the body. / 会话开始的连接期间，相机仍是本体。
        Entity view = focus != null ? focus : boundPlayer;
        boolean cameraTaken = client.getCameraEntity() != view;
        // A switch restarts the link in retarget(), so only an unchanged session can run out of grace.
        // 切换会在 retarget() 中重新开始连接，因此只有未变化的会话才会耗尽宽限。
        SeekerDeviceEntity target = sessionChanged ? null : resolveDevice(client.world, activeMode);
        SeekerRemoteViewRules.LinkStep step = sessionChanged ? SeekerRemoteViewRules.LinkStep.WAIT
                : SeekerRemoteViewRules.linkStep(target != null, target == device, waitedTicks + 1);
        SeekerRemoteViewRules.LocalExit exit = SeekerRemoteViewRules.localExit(player != boundPlayer, mode,
                sessionChanged, cameraTaken, step == SeekerRemoteViewRules.LinkStep.GIVE_UP, false);
        if (exit == SeekerRemoteViewRules.LocalExit.NONE && client.options.sneakKey.wasPressed()) {
            exit = SeekerRemoteViewRules.LocalExit.PLAYER_EXIT;
        }
        switch (exit) {
            case NONE -> {
                link(client, target, step);
                maintain(client, player);
                if (linked) {
                    SeekerCameraCycler.tick(client, player, activeMode);
                }
                return false;
            }
            case SWITCHED -> {
                retarget(client, player, mode, sessionId);
                maintain(client, player);
                return false;
            }
            default -> {
                int session = activeSessionId;
                end(client, exit.restoresCamera());
                if (exit.sendsClose()) {
                    sendClose(session);
                }
                if (exit != SeekerRemoteViewRules.LocalExit.PLAYER_CHANGED) {
                    markHandled(player, session);
                }
                if (exit == SeekerRemoteViewRules.LocalExit.FOCUS_LOST) {
                    // The server only hears a silent close, so the owner is told why here.
                    // 服务端只收到静默的关闭，因此在此告知拥有者原因。
                    client.inGameHud.setOverlayMessage(Text.translatable(SeekerExitReason.FOCUS_LOST.translationKey()),
                            false);
                }
                return true;
            }
        }
    }

    private static void tryStart(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode mode,
                                 int sessionId) {
        if (mode == SeekerSessionMode.NONE) {
            // Once the server reports NONE, any later session is new even if a reset reuses its id.
            // 服务端报告 NONE 之后，之后的任何会话都是新的，即使重置后复用了相同 id。
            handledPlayer = null;
            return;
        }
        if (handledPlayer == player && handledSessionId == sessionId) {
            return;
        }
        // The session is the server's word; its focus may stream in later (connecting), see the class doc.
        // 会话以服务端为准；其焦点可能稍后才推送到达（连接中），见类注释。
        markHandled(player, sessionId);
        start(client, player, mode, sessionId, resolveDevice(client.world, mode));
    }

    private static void start(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode mode,
                              int sessionId, @Nullable SeekerDeviceEntity target) {
        // Clear, never release: releasing a Wathe knife fires its stab. / 只清除、绝不释放：释放 Wathe 的刀会触发刺击。
        settleInput(client);
        closeGameplayScreen(client);
        drainQueuedPresses(client);
        WitchAbilityKeyBridge.reset();
        player.input = new SeekerFrozenInput();
        player.forwardSpeed = 0.0F;
        player.sidewaysSpeed = 0.0F;
        player.upwardSpeed = 0.0F;
        player.setJumping(false);
        player.setSprinting(false);
        Vec3d bodyVelocity = player.getVelocity();
        player.setVelocity(0.0, bodyVelocity.y, 0.0);
        savedChunkCulling = client.chunkCullingEnabled;
        client.chunkCullingEnabled = false;
        client.gameRenderer.setRenderHand(false);
        // The view holds the body itself from now on. / 从现在起由视角自行保持本体。
        SeekerBodyHold.reset();

        boundPlayer = player;
        activeMode = mode;
        activeSessionId = sessionId;
        focus = null;
        device = null;
        linked = false;
        waitedTicks = 0;
        boundSessionId = 0;
        boundDeviceId = -1;
        active = true;
        if (target == null) {
            // Connecting: the camera stays on the body under the CONNECTING panel. / 连接中：相机留在本体上，被“正在连接”面板遮住。
            waitedTicks = 1;
            return;
        }
        bind(client, mode, sessionId, target);
        bound(target);
        client.setCameraEntity(focus);
    }

    /**
     * Atomic switch (car to camera, camera to car, or camera to camera): point the view at the new focus in place,
     * without a full end/start cycle. A focus that has not streamed in yet keeps the last view (CONNECTING) until it
     * arrives; the old car goes back to tracker updates at once, since the server no longer takes its moves.
     * 原子切换（小车切到摄像头、摄像头切到小车，或摄像头切到另一台摄像头）：原地把视角指向新焦点，不走完整的结束/开始流程。
     * 尚未推送到达的焦点会保留上一画面（正在连接）直到其到达；旧小车立即交还给追踪包，因为服务端已不再接受它的移动。
     */
    private static void retarget(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode mode,
                                 int sessionId) {
        if (activeMode == SeekerSessionMode.CAR) {
            SeekerCarClientDriver.stop();
        }
        markHandled(player, sessionId);
        activeMode = mode;
        activeSessionId = sessionId;
        device = null;
        linked = false;
        SeekerDeviceEntity target = resolveDevice(client.world, mode);
        if (target == null) {
            waitedTicks = 1;
            return;
        }
        bind(client, mode, sessionId, target);
        bound(target);
        client.setCameraEntity(focus);
    }

    /**
     * Same session, one step of {@link SeekerRemoteViewRules#linkStep}: bind a focus that (re)appeared, keep waiting
     * while it is missing, or keep the bound one. A re-tracked car of this session keeps its move sequence (the server
     * admits only a rising seq within a session); anything else binds afresh.
     * 同一会话的一步 {@link SeekerRemoteViewRules#linkStep}：绑定（重新）出现的焦点、焦点缺失时继续等待，或保持已绑定的焦点。
     * 本会话重新追踪的小车沿用其移动序号（服务端在一个会话内只接受递增的 seq）；其余情况重新绑定。
     */
    private static void link(MinecraftClient client, @Nullable SeekerDeviceEntity target,
                             SeekerRemoteViewRules.LinkStep step) {
        switch (step) {
            case KEEP -> waitedTicks = 0;
            case BIND -> {
                if (target == null) {
                    return;
                }
                if (target instanceof SeekerCarEntity car && activeSessionId == boundSessionId
                        && car.getId() == boundDeviceId) {
                    device = car;
                    focus = car;
                    SeekerCarClientDriver.retrack(car);
                } else {
                    bind(client, activeMode, activeSessionId, target);
                }
                bound(target);
                client.setCameraEntity(focus);
            }
            case WAIT -> {
                waitedTicks++;
                if (linked) {
                    // Keep the last view; nothing is driven or looked at until the focus is back.
                    // 保留上一画面；焦点回来之前不驾驶、不上报视角。
                    linked = false;
                    device = null;
                    if (activeMode == SeekerSessionMode.CAR) {
                        SeekerCarClientDriver.stop();
                    }
                }
            }
            case GIVE_UP -> {
                // Handled by the caller as FOCUS_LOST. / 由调用方按 FOCUS_LOST 处理。
            }
        }
    }

    private static void bound(SeekerDeviceEntity target) {
        linked = true;
        waitedTicks = 0;
        boundSessionId = activeSessionId;
        boundDeviceId = target.getId();
        // Decided now, so a focus whose terrain is already here never flashes the mask.
        // 立即判定，使地形已到达的焦点绝不会闪现遮罩。
        revealed = false;
        revealTicks = 0;
        reveal();
    }

    /**
     * The focus is force-tracked before its chunks reach this client; keep CONNECTING until its surroundings arrive.
     * 焦点会在其区块到达本客户端之前被强制追踪；在其周围区块到达之前保持“正在连接”。
     */
    private static void reveal() {
        Entity current = focus;
        if (!linked || revealed || current == null) {
            return;
        }
        revealed = SeekerRemoteViewRules.revealsView(SeekerBodyHold.surroundingsLoaded(current), ++revealTicks);
    }

    /**
     * Binds the focus for a session start, an atomic switch, or a focus that streamed in again; the camera cycler
     * restarts its key edges and spacing here, so a strafe key held through the bind never cycles.
     * 为会话开始、原子切换或重新推送到达的焦点绑定焦点；摄像头切换器在此重置按键沿与发送间隔，因此绑定时一直按住的左右键不会
     * 触发切换。
     */
    private static void bind(MinecraftClient client, SeekerSessionMode mode, int sessionId, SeekerDeviceEntity target) {
        SeekerCameraCycler.rebind(client, boundPlayer);
        activeMode = mode;
        activeSessionId = sessionId;
        device = target;
        if (target instanceof SeekerCarEntity car) {
            focus = car;
            SeekerCarClientDriver.start(car, sessionId);
        } else {
            focus = new SeekerCameraViewpoint(client.world, (SeekerCameraEntity) target);
        }
    }

    private static void maintain(MinecraftClient client, ClientPlayerEntity player) {
        if (!(player.input instanceof SeekerFrozenInput)) {
            player.input = new SeekerFrozenInput();
        }
        player.setSprinting(false);
        client.chunkCullingEnabled = false;
        client.gameRenderer.setRenderHand(false);
        if (linked && focus instanceof SeekerCameraViewpoint viewpoint) {
            viewpoint.followMount();
            SeekerCameraLookSender.tick(viewpoint, activeSessionId);
        }
        reveal();
    }

    private static void end(MinecraftClient client, boolean restoreCamera) {
        Entity oldFocus = focus;
        ClientPlayerEntity bound = boundPlayer;
        boolean wasCar = activeMode == SeekerSessionMode.CAR;
        active = false;
        if (wasCar) {
            SeekerCarClientDriver.stop();
        }
        ClientPlayerEntity player = client.player;
        // Only hand back a camera that is still ours. / 只归还仍属于我们的相机。
        if (restoreCamera && player != null && oldFocus != null && client.getCameraEntity() == oldFocus) {
            client.setCameraEntity(player);
        }
        if (bound != null && bound.input instanceof SeekerFrozenInput) {
            bound.input = new KeyboardInput(client.options);
        }
        // setPressed(false) is a no-op on toggle keys; untoggle clears toggle sneak and sprint.
        // 切换式按键上 setPressed(false) 不起作用；untoggle 才能清除切换式潜行与疾跑。
        KeyBinding.untoggleStickyKeys();
        // Presses queued while input was locked must not replay. / 锁定期间积压的按键不得重放。
        drainQueuedPresses(client);
        client.chunkCullingEnabled = savedChunkCulling;
        if (client.gameRenderer != null) {
            client.gameRenderer.setRenderHand(true);
        }
        activeMode = SeekerSessionMode.NONE;
        activeSessionId = 0;
        focus = null;
        device = null;
        linked = false;
        waitedTicks = 0;
        boundSessionId = 0;
        boundDeviceId = -1;
        revealed = false;
        revealTicks = 0;
        boundPlayer = null;
        // The camera is back on the body before the server re-streams its chunks. / 相机先于服务端重新推送本体区块回到本体。
        SeekerBodyHold.beginSettle(bound);
    }

    @Nullable
    private static SeekerDeviceEntity resolveDevice(@Nullable ClientWorld world, SeekerSessionMode mode) {
        if (world == null) {
            return null;
        }
        int id = switch (mode) {
            case CAR -> SeekerClientState.carEntityId();
            // The synced session focus is the one camera the server opened. / 同步的会话焦点就是服务端打开的那台摄像头。
            case CAMERA -> SeekerClientState.hasCamera(SeekerClientState.sessionFocusEntityId())
                    ? SeekerClientState.sessionFocusEntityId() : -1;
            case NONE -> -1;
        };
        if (id < 0) {
            return null;
        }
        Entity entity = world.getEntityById(id);
        if (entity == null || entity.isRemoved()) {
            return null;
        }
        if (mode == SeekerSessionMode.CAR && entity instanceof SeekerCarEntity car) {
            return car;
        }
        if (mode == SeekerSessionMode.CAMERA && entity instanceof SeekerCameraEntity camera) {
            return camera;
        }
        return null;
    }

    /**
     * Pause and option screens stay; gameplay screens close. Wathe's inventory and shop are
     * {@link ScreenHandlerProvider}s but not vanilla handled screens, so they are matched by that interface.
     * 暂停与设置界面保留；游戏界面关闭。Wathe 的背包与商店实现 {@link ScreenHandlerProvider} 但并非原版 HandledScreen，因此按该接口判断。
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
            if (!SeekerRemoteViewRules.VOICE_CHAT_CATEGORY.equals(key.getCategory())) {
                ((SeekerRemoteKeyDrain) key).sparkwitch$drainPresses();
            }
        }
    }

    private static void sendClose(int sessionId) {
        if (ClientPlayNetworking.canSend(SeekerRemoteCloseC2SPacket.ID)) {
            ClientPlayNetworking.send(new SeekerRemoteCloseC2SPacket(sessionId));
        }
    }

    private static void markHandled(@Nullable ClientPlayerEntity player, int sessionId) {
        handledPlayer = player;
        handledSessionId = sessionId;
    }
}
