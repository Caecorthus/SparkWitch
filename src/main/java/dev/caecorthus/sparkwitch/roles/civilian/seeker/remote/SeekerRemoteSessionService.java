package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCameraRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.console.SeekerConsoleDevices;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarCorrectS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerNetworking;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteCloseC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteOpenC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frozen contract: the server-owned remote-view session (open validation, per-tick exit checks, sprint clear, end).
 * The camera is switched only on the owner's client; this service never switches the server-side camera entity, so
 * server camera writers (Taotie, Last Stand, Depression) keep working and the body is never dragged along.
 * Authority: the client never predicts entry; every exit except the owner's own Shift is detected here and reaches
 * the owner through the owner-only component sync.
 * 冻结契约：服务端持有的遥控视角会话（打开校验、逐刻退出检查、清除疾跑、结束）。
 * 相机只在拥有者客户端切换；本服务从不切换服务端相机实体，因此服务端相机写入方（饕餮、最后一搏、抑郁）照常工作，本体也不会被拖动。
 * 权威划分：客户端从不预测进入；除拥有者自己按 Shift 外，所有退出都在此检测，并经仅拥有者可见的组件同步下发。
 */
public final class SeekerRemoteSessionService {
    private static final String DENIED_KEY_PREFIX = "message.sparkwitch.seeker.remote.denied.";
    /** Server-only open throttle bookkeeping; weak so it never pins a player. / 仅服务端的打开节流记录，弱引用不会滞留玩家。 */
    private static final Map<PlayerEntity, Long> LAST_OPEN_TICK = new WeakHashMap<>();
    private static boolean registered;

    private SeekerRemoteSessionService() {
    }

    /**
     * Also registers the Q17 body push exemption ({@code SparkFactionApi.registerEntityCollisionExemption}: a player
     * in a Seeker session is not pushed). The predicate is side-neutral: the owner's client sees its own synced mode,
     * every other client sees NONE for other players, and the server sees the authoritative mode.
     * 同时注册 Q17 本体推挤豁免（处于搜寻者会话中的玩家不被推挤）。谓词两端通用：拥有者客户端读取自己同步来的模式，
     * 其他客户端看到的他人模式恒为 NONE，服务端读取权威模式。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerEntityCollisionExemption(entity ->
                entity instanceof PlayerEntity player && isInSession(player));
    }

    /**
     * Validates {@code seeker_remote_open} in the plan §3.6 order ({@link SeekerRemoteOpenRules}); on success opens a
     * new session (fresh sessionId, anchor = body position, attach deadline now + 40) or atomically switches mode or
     * camera. A CAMERA request without a target views {@link #defaultCamera}; an explicit target must be one of the
     * owner's cameras (never trusted beyond that: ownership, liveness, world, radius and play area are re-checked).
     * 按计划 §3.6 的顺序校验 {@code seeker_remote_open}；成功时打开新会话（新 sessionId、锚点为本体位置、挂接截止为当前 + 40 刻），
     * 或原子地切换模式或摄像头。未指定目标的摄像头请求观看 {@link #defaultCamera}；指定的目标必须是拥有者的摄像头之一
     * （除此之外不信任客户端：归属、存活、世界、半径与游戏区域都会重新校验）。
     */
    public static void handleOpen(ServerPlayerEntity player, SeekerRemoteOpenC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return;
        }
        long now = serverTick(player);
        SeekerSessionMode requested = packet.sessionMode();
        String commonDeny = SeekerTargeting.commonDenyReason(player);
        int radius = SeekerRules.effectiveRadius(SeekerRules.maxRadius(requested), engineViewDistance(player));
        Box playArea = playArea(player);
        SeekerDeviceEntity device = commonDeny == null
                ? usableDevice(player, status, requested, packet.targetEntityId(), radius, playArea) : null;
        Long lastOpen = LAST_OPEN_TICK.get(player);
        SeekerRemoteOpenRules.Facts facts = new SeekerRemoteOpenRules.Facts(
                commonDeny,
                requested,
                status.sessionMode(),
                device != null && status.sessionMode() == requested
                        && status.state().sessionFocusEntityId() == device.getId(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                SparkTraitsSeekerBridge.isLastStandPending(player),
                SeekerRemoteRules.isBodyGrounded(player.isOnGround(), player.hasVehicle(), player.isTouchingWater()),
                SeekerRemoteRules.isOpenThrottled(lastOpen == null ? -1L : lastOpen, now),
                SeekerConsoleDevices.hasConsoleDevice(player),
                device != null,
                device != null && SeekerRemoteRules.withinEffectiveRadius(player.getPos(), device.getPos(), radius),
                device != null && SeekerRemoteRules.insidePlayArea(playArea, device.getPos()));
        String deny = SeekerRemoteOpenRules.denyReason(facts);
        if (deny != null || device == null) {
            player.sendMessage(Text.translatable(DENIED_KEY_PREFIX
                    + (deny == null ? SeekerRemoteOpenRules.DENY_NO_DEVICE : deny)), true);
            return;
        }
        open(player, status, requested, device, radius, now);
    }

    /** Always accepted, even while stunned or feared; stale ids are ignored. / 始终接受，即使眩晕或恐惧；过期 id 忽略。 */
    public static void handleClose(ServerPlayerEntity player, SeekerRemoteCloseC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null || status.sessionMode() == SeekerSessionMode.NONE
                || packet.sessionId() != status.sessionId()) {
            return;
        }
        end(player, SeekerExitReason.PLAYER_EXIT);
    }

    /**
     * The one way any service ends a session, called BEFORE its own state transition: clears WP-09's
     * {@code SeekerSessionState}, applies {@code state.closeSession(reason)}, returns the car to idle, syncs, and sends
     * {@code reason.translationKey()} to the owner's action bar only when {@code reason.notifiesOwner()}. Idempotent; a
     * no-op when no session is open.
     * 任何服务结束会话的唯一途径，须在其自身状态转移之前调用：清除 WP-09 的会话状态、执行 closeSession、
     * 让小车回到空闲、同步，并仅在 {@code notifiesOwner()} 为真时向拥有者动作栏发送提示。幂等；无会话时为空操作。
     */
    public static void end(ServerPlayerEntity player, SeekerExitReason reason) {
        if (player == null || reason == null) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return;
        }
        status.setSessionState(null);
        SeekerSessionMode mode = status.sessionMode();
        if (mode == SeekerSessionMode.NONE) {
            return;
        }
        // apply() syncs the owner when the transition is dirty. / apply() 在状态有变化时同步给拥有者。
        status.apply(status.state().closeSession(reason));
        if (mode == SeekerSessionMode.CAR) {
            settleCar(player);
        }
        if (reason.notifiesOwner()) {
            player.sendMessage(Text.translatable(reason.translationKey()), true);
        }
    }

    /**
     * Side-neutral: reads the (owner-synced) component and {@code SeekerRules.isLocked}, which honours
     * {@code LOCK_SCOPE}. Other players' components read as unlocked on a client because they are never synced there.
     * 两端通用：读取（仅同步给拥有者的）组件与遵循 {@code LOCK_SCOPE} 的 {@code SeekerRules.isLocked}。
     * 他人的组件从不同步到本客户端，因此在客户端读取时视为未锁定。
     */
    public static boolean isLocked(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        return status != null && SeekerRules.isLocked(status.carState(), status.sessionMode());
    }

    /**
     * Called from the component's server tick (step 3): clears sprint while locked, drops a stale session state when
     * the synced mode is NONE, and ends the session with the first failing exit check (plan §3.11.1).
     * 由组件服务端刻调用（第 3 步）：锁定时清除疾跑；同步模式为 NONE 时丢弃残留会话状态；按计划 §3.11.1 以第一个失败的退出检查结束会话。
     */
    public static void tick(ServerPlayerEntity player, SeekerStatusComponent component) {
        if (player == null || component == null) {
            return;
        }
        if (player.isSprinting() && isLocked(player)) {
            player.setSprinting(false);
        }
        SeekerSessionMode mode = component.sessionMode();
        SeekerSessionState session = component.sessionState();
        if (mode == SeekerSessionMode.NONE) {
            if (session != null) {
                component.setSessionState(null);
            }
            return;
        }
        if (session == null || session.sessionId != component.sessionId() || session.mode != mode) {
            // The server lost its bookkeeping (e.g. a reload); never keep an unvalidated session.
            // 服务端丢失了会话记录（例如重载）；绝不保留未经校验的会话。
            end(player, SeekerExitReason.TIMEOUT);
            return;
        }
        SeekerExitReason reason = exitReason(player, component, session, serverTick(player));
        if (reason != null) {
            end(player, reason);
        }
    }

    // ---- Internals ----

    private static void open(ServerPlayerEntity player, SeekerStatusComponent status, SeekerSessionMode mode,
                             SeekerDeviceEntity device, int radius, long now) {
        // Atomic switch (mode or camera): openSession closes and reopens in one transition, so the owner never sees
        // NONE between. A switch keeps the previous anchor, so repeated switches cannot walk the frozen body away step
        // by step. The old bookkeeping is replaced only after the transition succeeded (a new sessionId); a refused
        // switch keeps the old session.
        // 原子切换（模式或摄像头）：openSession 在一次转移中关闭并重开，拥有者之间不会看到 NONE。切换沿用原锚点，
        // 反复切换无法让冻结的本体逐步走远。只有转移成功（产生新的 sessionId）后才替换旧记录；被拒绝的切换保留原会话。
        SeekerSessionState previous = status.sessionState();
        Vec3d anchor = SeekerRemoteOpenRules.isSwitch(status.sessionMode(), mode,
                status.state().sessionFocusEntityId(), device.getId()) && previous != null
                && previous.sessionId == status.sessionId() && previous.mode == status.sessionMode()
                ? previous.anchor : player.getPos();
        int previousSessionId = status.sessionId();
        status.apply(status.state().openSession(mode, device.getId(), radius));
        if (status.sessionMode() != mode || status.sessionId() == previousSessionId) {
            player.sendMessage(Text.translatable(DENIED_KEY_PREFIX + SeekerRemoteOpenRules.DENY_BLOCKED), true);
            return;
        }
        status.setSessionState(new SeekerSessionState(status.sessionId(), mode, device.getId(), radius,
                anchor, now, now + SeekerRules.ATTACH_TIMEOUT_TICKS));
        LAST_OPEN_TICK.put(player, now);
        player.setSprinting(false);
        if (player.isUsingItem()) {
            // Clear, never release: releasing a Wathe knife would stab. / 只清除、绝不释放：释放 Wathe 小刀会触发刺击。
            player.clearActiveItem();
        }
        if (mode == SeekerSessionMode.CAR && device instanceof SeekerCarEntity car) {
            SeekerNetworking.sendCorrection(player, new SeekerCarCorrectS2CPacket(status.sessionId(),
                    car.getX(), car.getY(), car.getZ(), car.getYaw()));
        }
    }

    @Nullable
    private static SeekerExitReason exitReason(ServerPlayerEntity player, SeekerStatusComponent status,
                                               SeekerSessionState session, long now) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        // STOPPING (the post-win fade) still counts as "running" for Wathe but fails the common gate; report it as the
        // silent ROUND_END rather than the notifying BLOCKED. / STOPPING（胜负后的淡出）对 Wathe 仍算“进行中”，
        // 但无法通过公共门槛；按静默的 ROUND_END 结束，而不是会提示的 BLOCKED。
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return SeekerExitReason.ROUND_END;
        }
        if (!SeekerRules.isSeeker(game.getRole(player))) {
            return SeekerExitReason.ROLE_CHANGED;
        }
        if (NoellesTaotieSeekerBridge.isSwallowed(player)) {
            return SeekerExitReason.BODY_SWALLOWED;
        }
        if (SparkTraitsSeekerBridge.isLastStandPending(player)) {
            return SeekerExitReason.LAST_STAND;
        }
        if (!player.isAlive() || game.isPlayerDead(player.getUuid())) {
            return SeekerExitReason.DIED;
        }
        if (player.isSpectator()) {
            return SeekerExitReason.SPECTATOR;
        }
        if (SeekerControlExpertBridge.isStunned(player)) {
            return SeekerExitReason.STUNNED;
        }
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        if (kidnap != null && kidnap.isControlled()) {
            return SeekerExitReason.KIDNAPPED;
        }
        if (GrandWitchFearService.isPlayerFeared(player)) {
            return SeekerExitReason.FEARED;
        }
        String deny = SeekerTargeting.commonDenyReason(player);
        if (deny != null) {
            return SeekerRemoteRules.exitReasonForDeny(deny);
        }
        if (!SeekerConsoleDevices.hasConsoleDevice(player)) {
            return SeekerExitReason.CONSOLE_LOST;
        }
        Entity focus = liveFocus(player, status, session);
        if (focus == null) {
            return SeekerExitReason.FOCUS_LOST;
        }
        if (SeekerRemoteRules.bodyMoved(session.anchor, player.getPos())) {
            return SeekerExitReason.BODY_MOVED;
        }
        if (SeekerRemoteRules.isBodyGrounded(player.isOnGround(), player.hasVehicle(), player.isTouchingWater())) {
            session.ungroundedTicks = 0;
        } else if (++session.ungroundedTicks > SeekerRemoteRules.UNGROUNDED_GRACE_TICKS) {
            return SeekerExitReason.BODY_MOVED;
        }
        if (!SeekerRemoteRules.withinSessionRange(player.getPos(), focus.getPos(), session.effectiveRadius)
                || !SeekerRemoteRules.insidePlayArea(playArea(player), focus.getPos())) {
            return SeekerExitReason.OUT_OF_RANGE;
        }
        if (session.mode == SeekerSessionMode.CAR
                && SeekerRemoteRules.carTimedOut(session.attached, session.attachDeadline, session.lastMoveTick, now)) {
            return SeekerExitReason.TIMEOUT;
        }
        return null;
    }

    /** The session's focus entity if it is still the owner's live device of the right kind. / 仍有效的焦点设备。 */
    @Nullable
    private static Entity liveFocus(ServerPlayerEntity player, SeekerStatusComponent status,
                                    SeekerSessionState session) {
        SeekerDeviceEntity device = switch (session.mode) {
            case CAR -> status.carState() == SeekerCarState.DEPLOYED && status.carEntityId() == session.focusEntityId
                    ? SeekerDeviceService.findCar(player) : null;
            case CAMERA -> SeekerDeviceService.findCamera(player, session.focusEntityId);
            case NONE -> null;
        };
        return device != null && device.getId() == session.focusEntityId && isOwnedAndAlive(player, device)
                ? device : null;
    }

    /**
     * Open target: exists, owned, alive, in the body's world and matching the synced id (CAMERA: the explicit target,
     * or {@link #defaultCamera}; the target id is ignored for CAR).
     * 打开目标的有效性（摄像头：指定的目标或 {@link #defaultCamera}；小车忽略目标 id）。
     */
    @Nullable
    private static SeekerDeviceEntity usableDevice(ServerPlayerEntity player, SeekerStatusComponent status,
                                                   SeekerSessionMode mode, int targetEntityId, int radius,
                                                   @Nullable Box playArea) {
        SeekerDeviceEntity device = switch (mode) {
            case CAR -> {
                if (status.carState() != SeekerCarState.DEPLOYED) {
                    yield null;
                }
                SeekerCarEntity car = SeekerDeviceService.findCar(player);
                yield car != null && car.getId() == status.carEntityId() ? car : null;
            }
            case CAMERA -> SeekerDeviceService.findCamera(player,
                    targetEntityId >= 0 ? targetEntityId : defaultCamera(player, status, radius, playArea));
            case NONE -> null;
        };
        return device != null && isOwnedAndAlive(player, device) ? device : null;
    }

    /**
     * The camera an untargeted CAMERA open views: the last-viewed camera if it is still usable (alive, in the body's
     * world, within the radius and the play area), otherwise the lowest-label usable one. With none usable it falls
     * back to the same order over merely live cameras, so the owner is told "out of range" rather than "no device".
     * 未指定目标的摄像头打开所观看的摄像头：最近观看的那台若仍可用（存活、与本体同世界、位于半径与游戏区域内）则选它，
     * 否则选编号最小的可用那台。都不可用时按同样顺序退回到仅存活的摄像头，使拥有者收到“超出范围”而非“设备不可用”。
     */
    private static int defaultCamera(ServerPlayerEntity player, SeekerStatusComponent status, int radius,
                                     @Nullable Box playArea) {
        int lastViewed = status.state().lastViewedCameraId();
        int reachable = SeekerCameraRules.defaultCamera(status.cameras(), lastViewed, id -> {
            SeekerCameraEntity camera = SeekerDeviceService.findCamera(player, id);
            return camera != null && isOwnedAndAlive(player, camera)
                    && SeekerRemoteRules.withinEffectiveRadius(player.getPos(), camera.getPos(), radius)
                    && SeekerRemoteRules.insidePlayArea(playArea, camera.getPos());
        });
        if (reachable >= 0) {
            return reachable;
        }
        return SeekerCameraRules.defaultCamera(status.cameras(), lastViewed, id -> {
            SeekerCameraEntity camera = SeekerDeviceService.findCamera(player, id);
            return camera != null && isOwnedAndAlive(player, camera);
        });
    }

    private static boolean isOwnedAndAlive(ServerPlayerEntity player, SeekerDeviceEntity device) {
        return !device.isRemoved() && device.isAlive() && device.getWorld() == player.getWorld()
                && player.getUuid().equals(device.ownerUuid());
    }

    private static boolean isInSession(PlayerEntity player) {
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        return status != null && status.sessionMode() != SeekerSessionMode.NONE;
    }

    /** Stops the car's driven motion so the idle server tick resumes cleanly. / 停止驾驶运动，使服务端空闲刻平稳接管。 */
    private static void settleCar(ServerPlayerEntity player) {
        SeekerCarEntity car = SeekerDeviceService.findCar(player);
        if (car != null && !car.isRemoved()) {
            car.setVelocity(new Vec3d(0.0, Math.min(0.0, car.getVelocity().y), 0.0));
        }
    }

    /**
     * The engine's tracking/chunk radius for this player, exactly as {@code ServerChunkLoadingManager#getViewDistance}:
     * the client's setting clamped to [2, server watch distance].
     * 该玩家的引擎追踪/区块半径，与 {@code ServerChunkLoadingManager#getViewDistance} 一致：客户端设置截断到 [2, 服务端视距]。
     */
    static int engineViewDistance(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        int serverDistance = server == null ? 32 : MathHelper.clamp(server.getPlayerManager().getViewDistance(), 2, 32);
        return MathHelper.clamp(player.getViewDistance(), 2, serverDistance);
    }

    @Nullable
    static Box playArea(PlayerEntity player) {
        return MapVariablesWorldComponent.KEY.get(player.getWorld()).getPlayArea();
    }

    static long serverTick(PlayerEntity player) {
        MinecraftServer server = player.getServer();
        return server != null ? server.getTicks() : player.getWorld().getTime();
    }
}
