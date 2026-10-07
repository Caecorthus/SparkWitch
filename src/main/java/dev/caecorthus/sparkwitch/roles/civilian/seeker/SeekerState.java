package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Frozen contract: the pure Seeker state machine (car, cameras, session, battery, mark). Transitions mutate this object
 * and return a {@link Delta}; the owning service applies cooldowns and side effects, the component syncs. A transition
 * that does not apply to the current state is a no-op returning {@link Delta#NONE}, so every caller may retry safely.
 * The owner may own any number of cameras; each gets a per-match label (1, 2, 3, ... in placement order, never reused
 * until {@link #clear()}, {@link #readNbt} or a new match in {@link #bindMatch}).
 * 冻结契约：纯搜寻者状态机（小车、摄像头、会话、电量、标记）。状态转移会修改本对象并返回 {@link Delta}；
 * 由负责的服务执行冷却与副作用，由组件同步。不适用于当前状态的转移为空操作并返回 {@link Delta#NONE}，调用方可安全重试。
 * 拥有者可以拥有任意数量的摄像头；每个摄像头按放置顺序获得本局编号（1、2、3……，在 {@link #clear()}、{@link #readNbt}
 * 或 {@link #bindMatch} 进入新对局之前从不复用）。
 *
 * <p>Owner sync (fixed write order, remaining ticks only, pinned by {@code SeekerComponentSchemaSourceTest}): carState
 * byte, carEntityId varint, cameraCount varint + (entityId varint, label varint) per camera in label order,
 * sessionMode byte, sessionId varint, sessionFocusEntityId varint (-1 without a session), cooldownReason byte,
 * markTarget boolean + UUID, markRemainingTicks varint, carBattery byte. The former effectiveRadius byte after the
 * focus id was dropped with the session radius (2026-10-04); it was sync-only (never NBT), and both sides ship in the
 * same jar. NBT keeps only {@code Match}, {@code LostTo} and {@code PendingReturn}; cameras never persist.
 * 拥有者同步（固定写入顺序，仅发送剩余刻数）见上：摄像头按编号顺序写入数量及每个摄像头的实体 id 与编号，
 * 并同步会话焦点实体 id（无会话时为 -1），使拥有者客户端准确知道自己正在观看哪台设备。焦点 id 之后原有的
 * effectiveRadius 字节已随会话半径一并移除（2026-10-04）；它只用于同步、从未写入 NBT，且两端来自同一个 jar。
 * NBT 只保存 {@code Match}、{@code LostTo} 与 {@code PendingReturn}；摄像头从不持久化。
 */
public final class SeekerState {
    static final String MATCH_NBT_KEY = "Match";
    static final String LOST_TO_NBT_KEY = "LostTo";
    static final String PENDING_RETURN_NBT_KEY = "PendingReturn";
    private static final int NO_ENTITY = -1;
    private static final int NO_WARNING = -1;
    private static final int FIRST_CAMERA_LABEL = 1;
    /**
     * Client-side guard against a corrupt camera count; far above any affordable number of cameras.
     * 客户端对异常摄像头数量的防护；远高于任何可负担的摄像头数量。
     */
    private static final int MAX_SYNCED_CAMERAS = 1024;

    /**
     * One placed camera of the owner: its entity id and its per-match label (shown as "CAM 01").
     * 拥有者的一台已放置摄像头：实体 id 与本局编号（显示为“摄像头 01”）。
     */
    public record Camera(int entityId, int label) {
    }

    /**
     * Result of one transition, always passed to {@code SeekerStatusComponent#apply}, which performs the one
     * {@code SeekerCooldowns.writeFloorExact} for {@code cooldownTicks}/{@code cooldownReason}. {@code batteryDepleted}
     * asks the component tick for {@code SeekerDeviceService.depleteCar}. {@code sessionClosed} is informational: a
     * transition closes a still-open session only as an idempotent safety net, because services call
     * {@code SeekerRemoteSessionService.end(owner, reason)} before the transition.
     * 单次状态转移的结果，始终交给 {@code SeekerStatusComponent#apply}，由其按冷却字段执行唯一一次
     * {@code SeekerCooldowns.writeFloorExact}。{@code batteryDepleted} 要求组件刻调用 {@code SeekerDeviceService.depleteCar}。
     * {@code sessionClosed} 仅供参考：各服务会先调用 {@code SeekerRemoteSessionService.end}，状态转移只作为幂等兜底关闭仍打开的会话。
     */
    public record Delta(boolean dirty, int cooldownTicks, SeekerCooldownReason cooldownReason, boolean sessionClosed,
                        @Nullable SeekerExitReason exitReason, boolean batteryDepleted) {
        public static final Delta NONE = new Delta(false, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    // ---- Owner-synced ----
    private SeekerCarState carState = SeekerCarState.NONE;
    private int carEntityId = NO_ENTITY;
    /** Ordered by label (placement order). / 按编号（放置顺序）排列。 */
    private final List<Camera> cameras = new ArrayList<>();
    private SeekerSessionMode sessionMode = SeekerSessionMode.NONE;
    private int sessionId;
    private int sessionFocusEntityId = NO_ENTITY;
    private SeekerCooldownReason cooldownReason = SeekerCooldownReason.NONE;
    private @Nullable UUID markTarget;
    private int markRemainingTicks;
    private int carBattery;

    // ---- Server-only, persisted ----
    private @Nullable UUID carLostTo;
    private boolean pendingReturn;
    private @Nullable String matchId;

    // ---- Server-only, transient ----
    private int nextCameraLabel = FIRST_CAMERA_LABEL;
    private int lastViewedCameraId = NO_ENTITY;
    private int batteryTicks;
    private boolean warnedLow;
    private boolean warnedCritical;

    public SeekerCarState carState() {
        return carState;
    }

    public int carEntityId() {
        return carEntityId;
    }

    /** The owner's cameras in label order (read-only view). / 按编号排列的拥有者摄像头（只读视图）。 */
    public List<Camera> cameras() {
        return Collections.unmodifiableList(cameras);
    }

    public int cameraCount() {
        return cameras.size();
    }

    public boolean hasCamera(int entityId) {
        return cameraLabel(entityId) > 0;
    }

    /** The camera's label, or -1 when it is not one of the owner's cameras. / 摄像头编号；不属于拥有者时为 -1。 */
    public int cameraLabel(int entityId) {
        if (entityId < 0) {
            return NO_ENTITY;
        }
        for (Camera camera : cameras) {
            if (camera.entityId() == entityId) {
                return camera.label();
            }
        }
        return NO_ENTITY;
    }

    /**
     * Server-only: the camera the owner viewed last (kept after the session ends), the default for an open request
     * without an explicit target; -1 when none. Never synced.
     * 仅服务端：拥有者最近观看的摄像头（会话结束后仍保留），是未指定目标的打开请求的默认选择；没有时为 -1。从不同步。
     */
    public int lastViewedCameraId() {
        return lastViewedCameraId;
    }

    public SeekerSessionMode sessionMode() {
        return sessionMode;
    }

    public int sessionId() {
        return sessionId;
    }

    public SeekerCooldownReason cooldownReason() {
        return cooldownReason;
    }

    @Nullable
    public UUID markTarget() {
        return markTarget;
    }

    public int markRemainingTicks() {
        return markRemainingTicks;
    }

    /** 0..100; meaningful only while DEPLOYED. / 0..100；仅在 DEPLOYED 时有意义。 */
    public int carBattery() {
        return carBattery;
    }

    @Nullable
    public UUID carLostTo() {
        return carLostTo;
    }

    public boolean pendingReturn() {
        return pendingReturn;
    }

    @Nullable
    public String matchId() {
        return matchId;
    }

    /**
     * Focus entity id recorded by {@link #openSession} (the car or the exact camera being viewed); -1 without a
     * session. Owner-synced so the owner's client knows which device it views.
     * 由 {@link #openSession} 记录的焦点实体 id（小车或正在观看的那台摄像头）；无会话时为 -1。
     * 同步给拥有者，使其客户端知道自己正在观看哪台设备。
     */
    public int sessionFocusEntityId() {
        return sessionFocusEntityId;
    }

    /**
     * True when this player holds no Seeker state at all (every non-Seeker). The component's per-tick early return.
     * 该玩家不持有任何搜寻者状态时为真（所有非搜寻者）；组件每刻的提前返回条件。
     */
    public boolean isIdle() {
        return carState == SeekerCarState.NONE
                && cameras.isEmpty()
                && sessionMode == SeekerSessionMode.NONE
                && markTarget == null
                && markRemainingTicks <= 0
                && carLostTo == null
                && !pendingReturn;
    }

    /** NONE → READY at the round-start grant (+INITIAL). / 开局发放：NONE → READY（+INITIAL）。 */
    public Delta grant() {
        if (carState != SeekerCarState.NONE) {
            return Delta.NONE;
        }
        carState = SeekerCarState.READY;
        return cooldown(SeekerCooldownReason.INITIAL, null);
    }

    /** READY → DEPLOYED with a full battery. / READY → DEPLOYED，电量充满。 */
    public Delta deploy(int carEntityId) {
        if (carState != SeekerCarState.READY || carEntityId < 0) {
            return Delta.NONE;
        }
        carState = SeekerCarState.DEPLOYED;
        this.carEntityId = carEntityId;
        carBattery = SeekerRules.BATTERY_MAX;
        batteryTicks = 0;
        warnedLow = false;
        warnedCritical = false;
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    /** DEPLOYED → READY (+RECALL 180 s); closes a CAR session with CAR_RECALLED. / 回收。 */
    public Delta recall() {
        return leaveDeployed(SeekerCooldownReason.RECALL, SeekerExitReason.CAR_RECALLED);
    }

    /** DEPLOYED → READY (+BROKEN 180 s); closes a CAR session with CAR_BROKEN. / 损坏。 */
    public Delta breakCar() {
        return leaveDeployed(SeekerCooldownReason.BROKEN, SeekerExitReason.CAR_BROKEN);
    }

    /** DEPLOYED → READY (+DEPLETED 180 s); closes a CAR session with BATTERY_DEPLETED. / 电量耗尽。 */
    public Delta deplete() {
        return leaveDeployed(SeekerCooldownReason.DEPLETED, SeekerExitReason.BATTERY_DEPLETED);
    }

    /**
     * DEPLOYED entity vanished unexpectedly → READY (+RECALL), never a free reset; closes a CAR session with FOCUS_LOST.
     * 部署中的实体异常丢失 → READY（+RECALL），绝不免费重置；以 FOCUS_LOST 结束小车会话。
     */
    public Delta loseCar() {
        return leaveDeployed(SeekerCooldownReason.RECALL, SeekerExitReason.FOCUS_LOST);
    }

    /** DEPLOYED → SWALLOWED, LostTo = taotie; no cooldown. / 被饕餮吞噬。 */
    public Delta swallow(UUID taotie) {
        if (carState != SeekerCarState.DEPLOYED || taotie == null) {
            return Delta.NONE;
        }
        boolean closed = closeIf(SeekerSessionMode.CAR);
        resetCar(SeekerCarState.SWALLOWED);
        carLostTo = taotie;
        return new Delta(true, 0, SeekerCooldownReason.NONE, closed, closed ? SeekerExitReason.CAR_SWALLOWED : null,
                false);
    }

    /**
     * SWALLOWED → READY (+RETURNED 60 s); clears LostTo and PendingReturn. Any other state is a no-op, so a stray
     * return can never reset a deployed car (orphaning its entity) or write a bogus RETURNED cooldown.
     * SWALLOWED → READY（+RETURNED 60 秒）；清除 LostTo 与 PendingReturn。其他状态为空操作，
     * 因此误发的归还永远不会重置已部署的小车（使其实体成为孤儿）或写入错误的 RETURNED 冷却。
     */
    public Delta returnCar() {
        if (carState != SeekerCarState.SWALLOWED) {
            return Delta.NONE;
        }
        resetCar(SeekerCarState.READY);
        carLostTo = null;
        pendingReturn = false;
        return cooldown(SeekerCooldownReason.RETURNED, null);
    }

    /**
     * Server-only retry flag owned by the Taotie return path; the car stays SWALLOWED until {@link #returnCar()}.
     * Setting it outside SWALLOWED is ignored (clearing is always allowed). Not synced, so the delta is never dirty.
     * 由饕餮归还路径使用的仅服务端重试标记；在 {@link #returnCar()} 之前小车保持 SWALLOWED。
     * 非 SWALLOWED 状态下的置位会被忽略（清除始终允许）。不同步，因此 delta 从不为 dirty。
     */
    public Delta setPendingReturn(boolean pending) {
        if (!pending || carState == SeekerCarState.SWALLOWED) {
            pendingReturn = pending;
        }
        return Delta.NONE;
    }

    /**
     * Adds a new camera with the next label; no cap (only money limits it). A known or negative id is a no-op.
     * 以下一个编号加入新摄像头；不设上限（只受金钱限制）。已登记或为负的 id 为空操作。
     */
    public Delta placeCamera(int cameraEntityId) {
        if (cameraEntityId < 0 || hasCamera(cameraEntityId)) {
            return Delta.NONE;
        }
        cameras.add(new Camera(cameraEntityId, nextCameraLabel++));
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    /**
     * Removes only that camera; closes the session with CAMERA_BROKEN only when it is showing that very camera.
     * 只移除该摄像头；仅当会话正显示这台摄像头时才以 CAMERA_BROKEN 结束会话。
     */
    public Delta destroyCamera(int cameraEntityId) {
        if (!hasCamera(cameraEntityId)) {
            return Delta.NONE;
        }
        boolean closed = sessionMode == SeekerSessionMode.CAMERA && sessionFocusEntityId == cameraEntityId
                && closeIf(SeekerSessionMode.CAMERA);
        cameras.removeIf(camera -> camera.entityId() == cameraEntityId);
        if (lastViewedCameraId == cameraEntityId) {
            lastViewedCameraId = NO_ENTITY;
        }
        return new Delta(true, 0, SeekerCooldownReason.NONE, closed, closed ? SeekerExitReason.CAMERA_BROKEN : null,
                false);
    }

    /**
     * Requires no session, a mode switch, or (CAMERA only) a different camera, and the viewed device present (CAR:
     * DEPLOYED; CAMERA: {@code focusEntityId} is one of the owner's cameras); increments sessionId. A switch reports
     * the previous session as closed with SWITCHED.
     * 要求当前无会话、为模式切换，或（仅 CAMERA）换到另一台摄像头，且目标设备存在（CAR 需 DEPLOYED，
     * CAMERA 需 {@code focusEntityId} 是拥有者的摄像头之一）；sessionId 递增。切换会把上一个会话报告为以 SWITCHED 关闭。
     */
    public Delta openSession(SeekerSessionMode mode, int focusEntityId) {
        if (mode == null || mode == SeekerSessionMode.NONE) {
            return Delta.NONE;
        }
        if (mode == sessionMode && (mode != SeekerSessionMode.CAMERA || focusEntityId == sessionFocusEntityId)) {
            return Delta.NONE;
        }
        boolean deviceReady = mode == SeekerSessionMode.CAR
                ? carState == SeekerCarState.DEPLOYED
                : hasCamera(focusEntityId);
        if (!deviceReady) {
            return Delta.NONE;
        }
        boolean switched = sessionMode != SeekerSessionMode.NONE;
        sessionMode = mode;
        sessionId++;
        sessionFocusEntityId = focusEntityId;
        if (mode == SeekerSessionMode.CAMERA) {
            lastViewedCameraId = focusEntityId;
        }
        return new Delta(true, 0, SeekerCooldownReason.NONE, switched, switched ? SeekerExitReason.SWITCHED : null,
                false);
    }

    /** Idempotent; sessionId stays monotonic. / 幂等；sessionId 保持单调。 */
    public Delta closeSession(SeekerExitReason reason) {
        if (sessionMode == SeekerSessionMode.NONE) {
            return Delta.NONE;
        }
        closeSessionFields();
        return new Delta(true, 0, SeekerCooldownReason.NONE, true, reason, false);
    }

    /**
     * One server tick of battery drain while DEPLOYED: 1% per 10 ticks when the session mode is CAR, else 1% per
     * 60 ticks; reports {@code batteryDepleted} when it reaches 0, and again after every further drain interval while
     * the car is somehow still DEPLOYED (a retry, never a second penalty: {@link #deplete()} applies once).
     * 部署期间每个服务端刻的电量消耗：会话模式为 CAR 时每 10 刻 1%，否则每 60 刻 1%；归零时报告 {@code batteryDepleted}，
     * 若小车仍处于部署状态，则每经过一个消耗间隔再报告一次（仅为重试，{@link #deplete()} 只会生效一次）。
     */
    public Delta tickBattery() {
        if (carState != SeekerCarState.DEPLOYED) {
            return Delta.NONE;
        }
        batteryTicks++;
        if (batteryTicks < SeekerRules.batteryDrainInterval(sessionMode == SeekerSessionMode.CAR)) {
            return Delta.NONE;
        }
        batteryTicks = 0;
        boolean changed = false;
        if (carBattery > 0) {
            carBattery = SeekerRules.clampBattery(carBattery - 1);
            changed = true;
        }
        return new Delta(changed, 0, SeekerCooldownReason.NONE, false, null, carBattery <= 0);
    }

    /**
     * Server only, called after {@link #tickBattery()}: returns the battery percent the first time per deploy it is at
     * or below 20% and again the first time at or below 10%, else -1. Drives the single owner warning (message + sound).
     * 仅服务端，在 {@link #tickBattery()} 之后调用：每次部署中电量首次 ≤20% 与首次 ≤10% 时各返回一次当前百分比，
     * 否则返回 -1。用于驱动唯一一次拥有者警告（消息与音效）。
     */
    public int pollBatteryWarning() {
        if (carState != SeekerCarState.DEPLOYED || carBattery <= 0) {
            return NO_WARNING;
        }
        if (!warnedCritical && SeekerRules.isBatteryCritical(carBattery)) {
            warnedCritical = true;
            warnedLow = true;
            return carBattery;
        }
        if (!warnedLow && SeekerRules.isBatteryWarning(carBattery)) {
            warnedLow = true;
            return carBattery;
        }
        return NO_WARNING;
    }

    /** Newest mark replaces the previous one. / 新标记替换旧标记。 */
    public Delta mark(UUID target, int ticks) {
        if (target == null || ticks <= 0) {
            return Delta.NONE;
        }
        markTarget = target;
        markRemainingTicks = ticks;
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    /**
     * Server countdown; dirty only when the mark expires (the client counts down locally between syncs).
     * 服务端倒计时；仅在标记到期时为 dirty（两次同步之间由客户端本地倒计时）。
     */
    public Delta tickMark() {
        if (markRemainingTicks <= 0) {
            return Delta.NONE;
        }
        markRemainingTicks--;
        if (markRemainingTicks > 0) {
            return Delta.NONE;
        }
        markTarget = null;
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    public Delta clearMark() {
        if (markTarget == null && markRemainingTicks <= 0) {
            return Delta.NONE;
        }
        markTarget = null;
        markRemainingTicks = 0;
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    /**
     * Any → NONE: final death, role change, reset, finalize. Keeps the match binding and the monotonic sessionId.
     * 任意 → NONE：最终死亡、职业变更、重置、结算。保留对局绑定与单调递增的 sessionId。
     */
    public Delta clear() {
        // Labels restart even for an idle state (never synced, so resetting them alone is not dirty).
        // 即使状态空闲也重新开始编号（编号不同步，因此单独重置不算 dirty）。
        nextCameraLabel = FIRST_CAMERA_LABEL;
        lastViewedCameraId = NO_ENTITY;
        if (isIdle() && cooldownReason == SeekerCooldownReason.NONE && carEntityId == NO_ENTITY
                && carBattery == 0) {
            return Delta.NONE;
        }
        boolean closed = sessionMode != SeekerSessionMode.NONE;
        closeSessionFields();
        resetCar(SeekerCarState.NONE);
        resetCameras();
        cooldownReason = SeekerCooldownReason.NONE;
        markTarget = null;
        markRemainingTicks = 0;
        carLostTo = null;
        pendingReturn = false;
        return new Delta(true, 0, SeekerCooldownReason.NONE, closed, null, false);
    }

    /**
     * Server-only binding; never synced. A new match with no cameras left restarts the camera labels, so they stay
     * per-match even for a state that was never cleared.
     * 仅服务端绑定，从不同步。进入新对局且没有剩余摄像头时重新开始摄像头编号，即使状态从未被清除，编号也按对局计算。
     */
    public Delta bindMatch(String matchId) {
        if (!Objects.equals(this.matchId, matchId) && cameras.isEmpty()) {
            nextCameraLabel = FIRST_CAMERA_LABEL;
            lastViewedCameraId = NO_ENTITY;
        }
        this.matchId = matchId;
        return Delta.NONE;
    }

    /** Client-side local countdown between syncs. / 两次同步之间的客户端本地倒计时。 */
    public void clientTick() {
        if (markRemainingTicks > 0) {
            markRemainingTicks--;
            if (markRemainingTicks == 0) {
                markTarget = null;
            }
        }
    }

    public void writeSync(RegistryByteBuf buf) {
        buf.writeByte(carState.id());
        buf.writeVarInt(carEntityId);
        buf.writeVarInt(cameras.size());
        for (Camera camera : cameras) {
            buf.writeVarInt(camera.entityId());
            buf.writeVarInt(camera.label());
        }
        buf.writeByte(sessionMode.id());
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sessionFocusEntityId);
        buf.writeByte(cooldownReason.id());
        boolean marked = markTarget != null && markRemainingTicks > 0;
        buf.writeBoolean(marked);
        if (marked) {
            buf.writeUuid(markTarget);
        }
        buf.writeVarInt(marked ? markRemainingTicks : 0);
        buf.writeByte(SeekerRules.clampBattery(carBattery));
    }

    public void readSync(RegistryByteBuf buf) {
        carState = SeekerCarState.fromId(buf.readUnsignedByte());
        carEntityId = buf.readVarInt();
        int cameraCount = buf.readVarInt();
        if (cameraCount < 0 || cameraCount > MAX_SYNCED_CAMERAS) {
            throw new IllegalStateException("Invalid Seeker camera count " + cameraCount);
        }
        cameras.clear();
        for (int index = 0; index < cameraCount; index++) {
            cameras.add(new Camera(buf.readVarInt(), buf.readVarInt()));
        }
        sessionMode = SeekerSessionMode.fromId(buf.readUnsignedByte());
        sessionId = buf.readVarInt();
        sessionFocusEntityId = buf.readVarInt();
        cooldownReason = SeekerCooldownReason.fromId(buf.readUnsignedByte());
        markTarget = buf.readBoolean() ? buf.readUuid() : null;
        markRemainingTicks = Math.max(0, buf.readVarInt());
        carBattery = SeekerRules.clampBattery(buf.readUnsignedByte());
    }

    public void writeNbt(NbtCompound nbt) {
        if (matchId != null) {
            nbt.putString(MATCH_NBT_KEY, matchId);
        }
        if (carLostTo != null) {
            nbt.putUuid(LOST_TO_NBT_KEY, carLostTo);
        }
        nbt.putBoolean(PENDING_RETURN_NBT_KEY, pendingReturn);
    }

    /**
     * Resets every transient and synced field, then restores the three persisted keys. A restored LostTo or
     * PendingReturn means the car is still owed, so it reads back as SWALLOWED; entities never survive a reload.
     * 重置所有瞬态与同步字段，再恢复三个持久化键。恢复出的 LostTo 或 PendingReturn 表示小车仍待归还，
     * 因此读回为 SWALLOWED；实体从不跨重载保留。
     */
    public void readNbt(NbtCompound nbt) {
        closeSessionFields();
        resetCar(SeekerCarState.NONE);
        resetCameras();
        cooldownReason = SeekerCooldownReason.NONE;
        markTarget = null;
        markRemainingTicks = 0;
        matchId = nbt.contains(MATCH_NBT_KEY, NbtElement.STRING_TYPE) ? nbt.getString(MATCH_NBT_KEY) : null;
        carLostTo = nbt.containsUuid(LOST_TO_NBT_KEY) ? nbt.getUuid(LOST_TO_NBT_KEY) : null;
        pendingReturn = nbt.getBoolean(PENDING_RETURN_NBT_KEY);
        if (carLostTo != null || pendingReturn) {
            carState = SeekerCarState.SWALLOWED;
        }
    }

    @Override
    public String toString() {
        return "SeekerState{car=" + carState + ", carId=" + carEntityId + ", cameras=" + cameras
                + ", session=" + sessionMode + "#" + sessionId + "@" + sessionFocusEntityId + ", battery=" + carBattery
                + ", lostTo=" + Objects.toString(carLostTo) + ", pendingReturn=" + pendingReturn + '}';
    }

    private Delta leaveDeployed(SeekerCooldownReason reason, SeekerExitReason exitReason) {
        if (carState != SeekerCarState.DEPLOYED) {
            return Delta.NONE;
        }
        boolean closed = closeIf(SeekerSessionMode.CAR);
        resetCar(SeekerCarState.READY);
        return cooldown(reason, closed ? exitReason : null);
    }

    private Delta cooldown(SeekerCooldownReason reason, @Nullable SeekerExitReason closedWith) {
        cooldownReason = reason;
        return new Delta(true, reason.ticks(), reason, closedWith != null, closedWith, false);
    }

    private boolean closeIf(SeekerSessionMode mode) {
        if (sessionMode != mode) {
            return false;
        }
        closeSessionFields();
        return true;
    }

    private void closeSessionFields() {
        sessionMode = SeekerSessionMode.NONE;
        sessionFocusEntityId = NO_ENTITY;
    }

    private void resetCameras() {
        cameras.clear();
        nextCameraLabel = FIRST_CAMERA_LABEL;
        lastViewedCameraId = NO_ENTITY;
    }

    private void resetCar(SeekerCarState next) {
        carState = next;
        carEntityId = NO_ENTITY;
        carBattery = 0;
        batteryTicks = 0;
        warnedLow = false;
        warnedCritical = false;
    }
}
