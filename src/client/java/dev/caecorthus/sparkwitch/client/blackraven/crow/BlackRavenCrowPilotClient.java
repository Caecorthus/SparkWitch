package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteKeyDrain;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowUseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowCorrectionS2CPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowDropFeatherC2SPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowMoveC2SPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowUseC2SPayload;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The owner's Raven Form view (化鸦): camera, flight prediction, input lock and the body hold, modelled on the SparkWitch
 * Seeker remote view (body stays {@code isCamera()} and frozen) and SparkStrength's drone pilot (flight and steering).
 * <p>
 * Authority: the server alone opens and ends a session; this client only reacts to the owner-synced
 * {@code sparkwitch:black_raven_crow} component (active bit + crow entity id). It predicts the crow it flies
 * ({@code BlackRavenCrowEntity.clientPilotMove}, capped like the server), streams {@code black_raven_crow_move} every
 * tick, applies {@code black_raven_crow_correction}, and sends right-clicks ({@code black_raven_crow_use}) and the drop
 * key ({@code black_raven_crow_drop_feather}); the server validates all of them. It never sends the toggle on its own
 * (a stray toggle would start a new session), so a view this client gives up on (camera taken, crow missing) simply
 * waits for the server's own end or the owner's next key-2 press. The camera switch is client-only.
 * <p>
 * Start edge (component active with a new crow id): clear (never release) the item in use, close gameplay screens,
 * drain the locked keys, install {@link BlackRavenCrowFrozenInput}, stop sprinting, disable chunk culling, hide the
 * hand, then look through the crow once it is on this client. Every tick: end when the player or world changed, the
 * server session ended or changed crow, the player died, the camera was taken, or the crow stayed missing past
 * {@link BlackRavenCrowClientRules#MISSING_GRACE_TICKS}. End edge: hand the camera back only while it is still ours,
 * restore the keyboard input, culling and hand, then hold the body until its chunks are back.
 * <p>
 * 本人的化鸦画面：镜头、飞行预测、输入锁与本体保持，仿照 SparkWitch 搜寻者遥控视角（本体保持 isCamera() 并冻结）与 SparkStrength
 * 无人机驾驶（飞行与转向）。权威：只有服务端能开始与结束会话；本客户端只响应仅同步给本人的组件（在场标记 + 乌鸦实体 id）。它预测自己
 * 操控的乌鸦（按服务端同样的上限截断），每刻上报 black_raven_crow_move，应用 black_raven_crow_correction，并发送右键
 * （black_raven_crow_use）与丢弃键（black_raven_crow_drop_feather）；全部由服务端校验。它从不自行发送切换包（多余的切换会开始新会话），
 * 因此本客户端放弃的画面（镜头被接管、乌鸦缺失）只等待服务端自行结束或本人再次按技能键 2。镜头切换只在客户端。
 * 开始沿：清除（绝不释放）使用中的物品、关闭游戏界面、清空被锁按键、装上冻结输入、停止疾跑、关闭区块剔除、隐藏手部，乌鸦到达本客户端后
 * 通过它观看。每刻：玩家或世界变化、服务端会话结束或换了乌鸦、玩家死亡、镜头被接管、乌鸦缺失超过宽限时结束。结束沿：仅当镜头仍属于我们
 * 时归还，恢复键盘输入、区块剔除与手部，然后保持本体直到其区块送达。
 */
public final class BlackRavenCrowPilotClient {
    private static boolean registered;
    /** Read by the input and render mixins. / 由输入与渲染 mixin 读取。 */
    private static volatile boolean active;
    private static int crowId = -1;
    private static volatile @Nullable BlackRavenCrowEntity crow;
    private static volatile @Nullable ClientPlayerEntity boundPlayer;
    private static @Nullable ClientWorld boundWorld;
    private static boolean savedChunkCulling = true;
    private static int missingTicks;
    private static Vec3d velocity = Vec3d.ZERO;
    private static boolean dropQueued;
    private static boolean useQueued;
    private static int featherPendingTicks;
    /** World time of the last use packet sent. / 上次发送使用包时的世界时间。 */
    private static long lastUseTick = Long.MIN_VALUE / 2;
    // A session this client already entered or gave up on is never re-entered. / 已进入或已放弃的会话不再进入。
    private static @Nullable ClientPlayerEntity handledPlayer;
    private static int handledCrowId = -1;
    // Post-session body hold. / 会话结束后的本体保持。
    private static @Nullable ClientPlayerEntity settleBody;
    private static int settleTicks;

    private BlackRavenCrowPilotClient() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Tracker moves are ignored only for the crow the local player's live session names (by id).
        // 只有本地玩家有效会话所指的乌鸦（按 id）忽略追踪位移。
        BlackRavenCrowEntity.installClientPilotHook(BlackRavenCrowPilotClient::pilots);
        // START_CLIENT_TICK runs before handleInputEvents; END_CLIENT_TICK after the world tick reset prev* fields.
        // START_CLIENT_TICK 早于 handleInputEvents；END_CLIENT_TICK 晚于世界刻重置 prev* 字段。
        ClientTickEvents.START_CLIENT_TICK.register(BlackRavenCrowPilotClient::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(BlackRavenCrowPilotClient::endTick);
        // Registering the receiver also makes the server's canSend check pass. / 注册接收器同时让服务端的 canSend 检查通过。
        ClientPlayNetworking.registerGlobalReceiver(BlackRavenCrowCorrectionS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> onCorrection(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forceExit());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forceExit());
    }

    // ---- Queries / 查询 ----

    /** True while the local player looks through (or is connecting to) its crow. / 本地玩家通过乌鸦观看（或连接中）时为真。 */
    public static boolean isActive() {
        return active;
    }

    /** The bound crow while active. / 激活时已绑定的乌鸦。 */
    public static @Nullable BlackRavenCrowEntity boundCrow() {
        return active ? crow : null;
    }

    /** The camera is the bound crow. / 镜头正是已绑定的乌鸦。 */
    public static boolean isViewingCrow() {
        BlackRavenCrowEntity bound = boundCrow();
        return bound != null && MinecraftClient.getInstance().getCameraEntity() == bound;
    }

    /**
     * The real local body while flying; its {@code isCamera()} stays true so it keeps sending (still) movement packets.
     * 飞行期间的真实本体；其 isCamera() 保持为真，从而持续发送（静止的）移动包。
     */
    public static boolean isPilotingBody(Object player) {
        return active && player != null && player == boundPlayer;
    }

    /**
     * {@code BlackRavenCrowBodyMixin}: the body's own SELF moves are cancelled for the whole session, and afterwards
     * until the chunks under it are back (the camera returns before the server re-streams them, and 1.21.1's
     * {@code ClientWorld.isChunkLoaded} is always true, so vanilla would let it fall through the empty chunk).
     * 本体自身的 SELF 移动在整个会话期间被取消，会话结束后保持到其下方区块送达（镜头先于服务端重新推送区块归位，而 1.21.1 的
     * ClientWorld.isChunkLoaded 恒为真，原版会让本体穿过空区块下落）。
     */
    public static boolean freezesBody(ClientPlayerEntity player) {
        if (player != MinecraftClient.getInstance().player) {
            return false;
        }
        if (active && player == boundPlayer) {
            return true;
        }
        return settleTicks > 0 && player == settleBody && !bodyChunksLoaded(player);
    }

    /** Locked keys read as released while active. / 激活期间被锁定的按键视为未按下。 */
    public static boolean locksKey(KeyBinding key) {
        return active && BlackRavenCrowClientRules.isLockedKey(key.getTranslationKey());
    }

    /**
     * {@code BlackRavenCrowKeyBindingMixin}: a locked key's queued presses were swallowed; a drop or use press becomes
     * the crow's own action. / 被锁按键的积压按下已被吞掉；丢弃或使用键的按下转为乌鸦自己的动作。
     */
    public static void onSwallowedPresses(KeyBinding key, int presses) {
        if (!active || presses <= 0) {
            return;
        }
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) {
            return;
        }
        if (key == options.dropKey) {
            dropQueued = true;
        } else if (key == options.useKey) {
            useQueued = true;
        }
    }

    // ---- Mixin hooks / mixin 钩子 ----

    /**
     * Mouse look turns the crow, never the body. Returns true when consumed (always while active).
     * 鼠标视角转动乌鸦而非本体。激活期间总是返回 true（已接管）。
     */
    public static boolean steer(double cursorDeltaX, double cursorDeltaY) {
        if (!active) {
            return false;
        }
        BlackRavenCrowEntity bound = crow;
        if (bound != null && MinecraftClient.getInstance().getCameraEntity() == bound) {
            bound.changeLookDirection(cursorDeltaX, cursorDeltaY);
            bound.setPitch(BlackRavenCrowClientRules.clampPitch(bound.getPitch()));
            bound.prevPitch = BlackRavenCrowClientRules.clampPitch(bound.prevPitch);
        }
        return true;
    }

    /**
     * {@code handleInputEvents} HEAD: settle the body, take the drop/use presses (also the ones another hook left
     * queued) and turn them into the crow's feather drop and block use.
     * handleInputEvents 开头：安顿本体，取走丢弃/使用键的按下（包括其他钩子留下的），转为乌鸦的丢羽与方块交互。
     */
    public static void captureInput(MinecraftClient client) {
        if (!active) {
            return;
        }
        settleInput(client);
        GameOptions options = client.options;
        if (((SeekerRemoteKeyDrain) options.dropKey).sparkwitch$takePresses() > 0) {
            dropQueued = true;
        }
        if (((SeekerRemoteKeyDrain) options.useKey).sparkwitch$takePresses() > 0) {
            useQueued = true;
        }
        drainLockedKeys(client);
        if (dropQueued) {
            dropQueued = false;
            requestDrop(client);
        }
        if (useQueued) {
            useQueued = false;
            requestUse();
        }
    }

    /**
     * {@code updateCrosshairTarget} RETURN: the crosshair target is the block the crow's eye looks at within
     * {@link BlackRavenCrowClientRules#USE_REACH}, never an entity, so nothing near the crow can be attacked, outlined or
     * picked by other abilities. / 准星目标为乌鸦眼睛在使用距离内看着的方块，从不是实体。
     */
    public static void overrideCrosshairTarget(MinecraftClient client, float tickDelta) {
        if (!active) {
            return;
        }
        Entity camera = client.getCameraEntity();
        BlackRavenCrowEntity bound = crow;
        BlockHitResult hit = bound != null && camera == bound ? crowBlockTarget(bound, tickDelta) : null;
        if (hit != null) {
            client.crosshairTarget = hit;
        } else {
            Vec3d at = camera != null ? camera.getCameraPosVec(tickDelta) : Vec3d.ZERO;
            client.crosshairTarget = BlockHitResult.createMissed(at, Direction.UP, BlockPos.ofFloored(at));
        }
        client.targetedEntity = null;
    }

    /**
     * Render-grid centre axis (0/1/2 = x/y/z) for vanilla {@code setupTerrain}: the camera while it looks through the
     * crow, otherwise the original player coordinate. / 原版 setupTerrain 的渲染网格中心坐标：通过乌鸦观看时取镜头位置。
     */
    public static double renderGridCenter(double original, int axis) {
        if (!isViewingCrow()) {
            return original;
        }
        Vec3d position = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        return axis == 0 ? position.x : axis == 1 ? position.y : position.z;
    }

    /**
     * {@code onSetCameraEntity} after the main-thread hop: a server camera writer takes over. The view ends without
     * restoring the camera; when vanilla is about to ignore the packet ({@code next == null}) the camera goes back to
     * the body instead. / 服务端相机写入方接管：视角结束且不归还镜头；原版将忽略该包（next 为 null）时改为归还本体。
     */
    public static void yieldCamera(@Nullable Entity next) {
        if (!active) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = boundPlayer;
        int session = crowId;
        end(client, next == null);
        markHandled(player, session);
    }

    /** Disconnect, join, respawn: drop everything without sending. / 断线、加入、重生：清空状态，不发送任何包。 */
    public static void forceExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (!client.isOnThread()) {
            client.execute(BlackRavenCrowPilotClient::forceExit);
            return;
        }
        if (active) {
            end(client, true);
        }
        settleBody = null;
        settleTicks = 0;
        handledPlayer = null;
        handledCrowId = -1;
    }

    /** The feather slot shows the Homing Feather. / 归羽格显示归羽。 */
    public static boolean showsFeather(@Nullable ClientPlayerEntity player) {
        BlackRavenCrowPlayerComponent component = player == null ? null
                : BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        return component != null && BlackRavenCrowClientRules.showsFeather(component.hasFeather(), featherPendingTicks);
    }

    /**
     * The crow's eye looks, within reach, at a block the crow can actually use (door, trapdoor, gate, button, lever).
     * 乌鸦眼睛在使用距离内看着一个乌鸦真正能使用的方块（门、活板门、栅栏门、按钮、拉杆）。
     */
    public static boolean targetsBlock(MinecraftClient client) {
        return active && client.world != null
                && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && BlackRavenCrowUseRules.target(client.world.getBlockState(hit.getBlockPos())) != null;
    }

    // ---- Packets / 数据包 ----

    private static boolean pilots(BlackRavenCrowEntity candidate) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || candidate.getWorld() != player.getWorld()) {
            return false;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        return component != null && component.isActive() && component.getCrowEntityId() == candidate.getId()
                && candidate.isOwnedBy(player.getUuid());
    }

    private static void onCorrection(MinecraftClient client, BlackRavenCrowCorrectionS2CPayload payload) {
        if (client.world == null) {
            return;
        }
        Entity entity = client.world.getEntityById(payload.crowEntityId());
        if (!(entity instanceof BlackRavenCrowEntity target) || !target.isLocallyPiloted()) {
            return;
        }
        target.clientApplyCorrection(payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch());
        if (target == crow) {
            velocity = Vec3d.ZERO;
        }
    }

    private static void requestDrop(MinecraftClient client) {
        BlackRavenCrowEntity bound = crow;
        BlackRavenCrowPlayerComponent component = client.player == null ? null
                : BlackRavenCrowPlayerComponent.KEY.getNullable(client.player);
        if (bound == null || component == null || client.getCameraEntity() != bound
                || !BlackRavenCrowClientRules.sendsDrop(component.hasFeather(), featherPendingTicks)
                || !ClientPlayNetworking.canSend(BlackRavenCrowDropFeatherC2SPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new BlackRavenCrowDropFeatherC2SPayload());
        featherPendingTicks = BlackRavenCrowClientRules.FEATHER_PENDING_TICKS;
    }

    private static void requestUse() {
        BlackRavenCrowEntity bound = crow;
        if (bound == null || MinecraftClient.getInstance().getCameraEntity() != bound) {
            return;
        }
        // Client pacing matches the server's use interval, so fast clicks are not silently throttled there.
        // 客户端节流与服务端的使用间隔一致，避免快速连点在服务端被静默丢弃。
        long now = bound.getWorld().getTime();
        if (now >= lastUseTick && now - lastUseTick < BlackRavenCrowUseRules.USE_INTERVAL_TICKS) {
            return;
        }
        BlockHitResult hit = crowBlockTarget(bound, 1.0F);
        if (hit != null && ClientPlayNetworking.canSend(BlackRavenCrowUseC2SPayload.ID)) {
            ClientPlayNetworking.send(new BlackRavenCrowUseC2SPayload(bound.getId(), hit));
            lastUseTick = now;
        }
    }

    // ---- Lifecycle / 生命周期 ----

    private static void startTick(MinecraftClient client) {
        settle(client);
        ClientPlayerEntity player = client.player;
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null || client.world == null) {
            if (active) {
                end(client, true);
            }
            return;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        int sessionCrow = component != null && component.isActive() ? component.getCrowEntityId() : -1;
        if (active) {
            boolean sameSession = player == boundPlayer && client.world == boundWorld && sessionCrow == crowId
                    && player.isAlive() && !player.isSpectator();
            if (sameSession) {
                maintain(client, player);
                return;
            }
            end(client, true);
        }
        if (sessionCrow < 0) {
            // Once the server reports no session, a later one is new even if an id repeats.
            // 服务端报告无会话之后，之后的会话都是新的，即使 id 重复。
            handledPlayer = null;
            handledCrowId = -1;
            return;
        }
        if ((handledPlayer == player && handledCrowId == sessionCrow) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        start(client, player, sessionCrow);
    }

    private static void start(MinecraftClient client, ClientPlayerEntity player, int sessionCrow) {
        // Clear, never release: releasing a Wathe knife fires its stab. / 只清除、绝不释放：释放 Wathe 的刀会触发刺击。
        settleInput(client);
        closeGameplayScreen(client);
        drainLockedKeys(client);
        KeyBinding.untoggleStickyKeys();
        player.input = new BlackRavenCrowFrozenInput();
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
        settleBody = null;
        settleTicks = 0;

        crowId = sessionCrow;
        crow = null;
        boundPlayer = player;
        boundWorld = client.world;
        missingTicks = 0;
        velocity = Vec3d.ZERO;
        dropQueued = false;
        useQueued = false;
        featherPendingTicks = 0;
        active = true;
        markHandled(player, sessionCrow);
        link(client);
    }

    private static void maintain(MinecraftClient client, ClientPlayerEntity player) {
        if (!(player.input instanceof BlackRavenCrowFrozenInput)) {
            player.input = new BlackRavenCrowFrozenInput();
        }
        player.setSprinting(false);
        client.chunkCullingEnabled = false;
        client.gameRenderer.setRenderHand(false);
        link(client);
    }

    /**
     * Binds the crow by id every tick (a re-tracked crow is a new instance with the same id) and keeps the camera on
     * it. A camera that is neither the body, the previous crow nor this one was taken by something else: the view ends
     * without restoring it. / 每刻按 id 绑定乌鸦（重新追踪的乌鸦是同 id 的新实例）并保持镜头在其上；镜头既不是本体、旧乌鸦也不是
     * 当前乌鸦时说明被其他功能接管，视角结束且不归还。
     */
    private static void link(MinecraftClient client) {
        ClientWorld world = client.world;
        Entity found = world == null ? null : world.getEntityById(crowId);
        BlackRavenCrowEntity previous = crow;
        Entity camera = client.getCameraEntity();
        boolean ours = camera == boundPlayer || (previous != null && camera == previous)
                || (found != null && camera == found);
        if (!ours) {
            ClientPlayerEntity player = boundPlayer;
            int session = crowId;
            end(client, false);
            markHandled(player, session);
            return;
        }
        if (!(found instanceof BlackRavenCrowEntity current) || current.isRemoved()) {
            velocity = Vec3d.ZERO;
            if (++missingTicks > BlackRavenCrowClientRules.MISSING_GRACE_TICKS) {
                ClientPlayerEntity player = boundPlayer;
                int session = crowId;
                end(client, true);
                markHandled(player, session);
            }
            return;
        }
        missingTicks = 0;
        if (previous != current) {
            if (previous != null) {
                // Re-tracked instance: keep the owner's look. / 重新追踪的新实例：保留本人的朝向。
                current.setYaw(previous.getYaw());
                current.setPitch(previous.getPitch());
                current.prevYaw = previous.getYaw();
                current.prevPitch = previous.getPitch();
            } else {
                current.setPitch(BlackRavenCrowClientRules.clampPitch(current.getPitch()));
            }
            crow = current;
        }
        if (client.getCameraEntity() != current) {
            client.setCameraEntity(current);
        }
    }

    private static void end(MinecraftClient client, boolean restoreCamera) {
        BlackRavenCrowEntity old = crow;
        ClientPlayerEntity body = boundPlayer;
        active = false;
        crow = null;
        crowId = -1;
        ClientPlayerEntity player = client.player;
        // Only hand back a camera that is still ours. / 只归还仍属于我们的镜头。
        if (restoreCamera && player != null) {
            Entity camera = client.getCameraEntity();
            if (camera == null || camera == old || camera instanceof BlackRavenCrowEntity) {
                client.setCameraEntity(player);
            }
        }
        if (body != null && body.input instanceof BlackRavenCrowFrozenInput) {
            body.input = new KeyboardInput(client.options);
        }
        // setPressed(false) is a no-op on toggle keys; untoggle clears toggle sneak and sprint.
        // 切换式按键上 setPressed(false) 无效；untoggle 才能清除切换式潜行与疾跑。
        KeyBinding.untoggleStickyKeys();
        // Presses queued while locked must not replay on the body. / 锁定期间积压的按下不得在本体上重放。
        drainLockedKeys(client);
        client.chunkCullingEnabled = savedChunkCulling;
        if (client.gameRenderer != null) {
            client.gameRenderer.setRenderHand(true);
        }
        velocity = Vec3d.ZERO;
        dropQueued = false;
        useQueued = false;
        featherPendingTicks = 0;
        missingTicks = 0;
        boundPlayer = null;
        boundWorld = null;
        settleBody = body;
        settleTicks = body == null ? 0 : BlackRavenCrowClientRules.BODY_SETTLE_MAX_TICKS;
    }

    private static void settle(MinecraftClient client) {
        if (settleTicks <= 0) {
            return;
        }
        ClientPlayerEntity body = client.player;
        if (body == null || body != settleBody || --settleTicks <= 0 || bodyChunksLoaded(body)) {
            settleBody = null;
            settleTicks = 0;
        }
    }

    private static void markHandled(@Nullable ClientPlayerEntity player, int session) {
        handledPlayer = player;
        handledCrowId = session;
    }

    // ---- Flight / 飞行 ----

    private static void endTick(MinecraftClient client) {
        if (featherPendingTicks > 0) {
            featherPendingTicks--;
        }
        if (!active) {
            return;
        }
        BlackRavenCrowEntity bound = crow;
        if (bound == null || bound.isRemoved() || client.getCameraEntity() != bound || client.player == null) {
            velocity = Vec3d.ZERO;
            return;
        }
        fly(client, bound);
    }

    private static void fly(MinecraftClient client, BlackRavenCrowEntity bound) {
        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;
        if (client.currentScreen == null && client.getOverlay() == null) {
            GameOptions options = client.options;
            forward = BlackRavenCrowClientRules.axis(physicallyHeld(client, options.forwardKey),
                    physicallyHeld(client, options.backKey));
            strafe = BlackRavenCrowClientRules.axis(physicallyHeld(client, options.leftKey),
                    physicallyHeld(client, options.rightKey));
            vertical = BlackRavenCrowClientRules.axis(physicallyHeld(client, options.jumpKey),
                    physicallyHeld(client, options.sneakKey));
        }
        Vec3d target = BlackRavenCrowClientRules.targetVelocity(forward, strafe, vertical, bound.getYaw());
        velocity = BlackRavenCrowClientRules.nextVelocity(velocity, target);
        Vec3d before = bound.getPos();
        bound.clientPilotMove(velocity, bound.getYaw(), bound.getPitch());
        // Collisions and the bounds clamp eat momentum. / 碰撞与边界限制会吃掉动量。
        velocity = bound.getPos().subtract(before);
        if (ClientPlayNetworking.canSend(BlackRavenCrowMoveC2SPayload.ID)) {
            ClientPlayNetworking.send(new BlackRavenCrowMoveC2SPayload(bound.getId(), bound.getX(), bound.getY(),
                    bound.getZ(), MathHelper.wrapDegrees(bound.getYaw()), bound.getPitch()));
        }
    }

    // ---- Helpers / 辅助 ----

    /** Block the crow's eye looks at within reach, or null. / 乌鸦眼睛在使用距离内看着的方块，没有时为 null。 */
    static @Nullable BlockHitResult crowBlockTarget(BlackRavenCrowEntity bound, float tickDelta) {
        Vec3d eye = bound.getCameraPosVec(tickDelta);
        Vec3d end = eye.add(bound.getRotationVec(tickDelta).multiply(BlackRavenCrowClientRules.USE_REACH));
        BlockHitResult hit = bound.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, bound));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * Physical state of a binding's key from GLFW, bypassing the lock and Wathe's in-round jump suppression (jump is
     * the crow's "up"). An unbound or scancode-only key reads as up.
     * 从 GLFW 读取按键绑定的物理状态，绕过锁定与 Wathe 对局内的跳跃屏蔽（跳跃是乌鸦的"上升"）。未绑定或仅有扫描码的按键视为松开。
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

    private static void settleInput(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && player.isUsingItem()) {
            player.clearActiveItem();
        }
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    /**
     * Pause and option screens stay; gameplay screens close. Wathe's inventory and shop are
     * {@link ScreenHandlerProvider}s but not vanilla handled screens. / 暂停与设置界面保留；游戏界面关闭。
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

    private static void drainLockedKeys(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        for (KeyBinding key : client.options.allKeys) {
            if (BlackRavenCrowClientRules.isLockedKey(key.getTranslationKey())) {
                ((SeekerRemoteKeyDrain) key).sparkwitch$drainPresses();
            }
        }
    }

    private static boolean bodyChunksLoaded(ClientPlayerEntity body) {
        Box box = body.getBoundingBox();
        ChunkManager chunks = body.getWorld().getChunkManager();
        int minX = ChunkSectionPos.getSectionCoord(box.minX);
        int maxX = ChunkSectionPos.getSectionCoord(box.maxX);
        int minZ = ChunkSectionPos.getSectionCoord(box.minZ);
        int maxZ = ChunkSectionPos.getSectionCoord(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!chunks.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
