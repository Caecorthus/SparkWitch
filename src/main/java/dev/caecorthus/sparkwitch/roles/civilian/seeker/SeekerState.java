package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Frozen contract: the pure Seeker state machine (car, camera, session, battery, mark). Transitions mutate this object
 * and return a {@link Delta}; the owning service applies cooldowns and side effects, the component syncs. A transition
 * that does not apply to the current state is a no-op returning {@link Delta#NONE}, so every caller may retry safely.
 * 冻结契约：纯搜寻者状态机（小车、摄像头、会话、电量、标记）。状态转移会修改本对象并返回 {@link Delta}；
 * 由负责的服务执行冷却与副作用，由组件同步。不适用于当前状态的转移为空操作并返回 {@link Delta#NONE}，调用方可安全重试。
 *
 * <p>Owner sync (fixed write order, remaining ticks only, pinned by {@code SeekerComponentSchemaSourceTest}): carState
 * byte, carEntityId varint, cameraEntityId varint, sessionMode byte, sessionId varint, effectiveRadius byte,
 * cooldownReason byte, markTarget boolean + UUID, markRemainingTicks varint, carBattery byte. NBT keeps only
 * {@code Match}, {@code LostTo} and {@code PendingReturn}.
 * 拥有者同步（固定写入顺序，仅发送剩余刻数）见上；NBT 只保存 {@code Match}、{@code LostTo} 与 {@code PendingReturn}。
 */
public final class SeekerState {
    static final String MATCH_NBT_KEY = "Match";
    static final String LOST_TO_NBT_KEY = "LostTo";
    static final String PENDING_RETURN_NBT_KEY = "PendingReturn";
    private static final int NO_ENTITY = -1;
    private static final int NO_WARNING = -1;
    private static final int MAX_RADIUS_BYTE = 255;

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
    private int cameraEntityId = NO_ENTITY;
    private SeekerSessionMode sessionMode = SeekerSessionMode.NONE;
    private int sessionId;
    private int effectiveRadius;
    private SeekerCooldownReason cooldownReason = SeekerCooldownReason.NONE;
    private @Nullable UUID markTarget;
    private int markRemainingTicks;
    private int carBattery;

    // ---- Server-only, persisted ----
    private @Nullable UUID carLostTo;
    private boolean pendingReturn;
    private @Nullable String matchId;

    // ---- Server-only, transient ----
    private int sessionFocusEntityId = NO_ENTITY;
    private int batteryTicks;
    private boolean warnedLow;
    private boolean warnedCritical;

    public SeekerCarState carState() {
        return carState;
    }

    public int carEntityId() {
        return carEntityId;
    }

    public int cameraEntityId() {
        return cameraEntityId;
    }

    public SeekerSessionMode sessionMode() {
        return sessionMode;
    }

    public int sessionId() {
        return sessionId;
    }

    public int effectiveRadius() {
        return effectiveRadius;
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
     * Server-only focus entity id recorded by {@link #openSession}; -1 without a session. Never synced.
     * 由 {@link #openSession} 记录的仅服务端焦点实体 id；无会话时为 -1，从不同步。
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
                && cameraEntityId == NO_ENTITY
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

    /** At most one camera per owner. / 每名拥有者最多一个摄像头。 */
    public Delta placeCamera(int cameraEntityId) {
        if (this.cameraEntityId != NO_ENTITY || cameraEntityId < 0) {
            return Delta.NONE;
        }
        this.cameraEntityId = cameraEntityId;
        return new Delta(true, 0, SeekerCooldownReason.NONE, false, null, false);
    }

    /** Closes a CAMERA session with CAMERA_BROKEN. / 结束摄像头会话。 */
    public Delta destroyCamera() {
        if (cameraEntityId == NO_ENTITY) {
            return Delta.NONE;
        }
        boolean closed = closeIf(SeekerSessionMode.CAMERA);
        cameraEntityId = NO_ENTITY;
        return new Delta(true, 0, SeekerCooldownReason.NONE, closed, closed ? SeekerExitReason.CAMERA_BROKEN : null,
                false);
    }

    /**
     * Requires no session or a mode switch, and the viewed device present (CAR: DEPLOYED; CAMERA: placed); increments
     * sessionId. A switch reports the previous session as closed with SWITCHED. The radius is clamped to one byte.
     * 要求当前无会话或为模式切换，且目标设备存在（CAR 需 DEPLOYED，CAMERA 需已放置）；sessionId 递增。
     * 模式切换会把上一个会话报告为以 SWITCHED 关闭。半径被限制在一个字节内。
     */
    public Delta openSession(SeekerSessionMode mode, int focusEntityId, int effectiveRadius) {
        if (mode == null || mode == SeekerSessionMode.NONE || mode == sessionMode) {
            return Delta.NONE;
        }
        boolean deviceReady = mode == SeekerSessionMode.CAR
                ? carState == SeekerCarState.DEPLOYED
                : cameraEntityId != NO_ENTITY;
        if (!deviceReady) {
            return Delta.NONE;
        }
        boolean switched = sessionMode != SeekerSessionMode.NONE;
        sessionMode = mode;
        sessionId++;
        sessionFocusEntityId = focusEntityId;
        this.effectiveRadius = Math.max(0, Math.min(MAX_RADIUS_BYTE, effectiveRadius));
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
        if (isIdle() && cooldownReason == SeekerCooldownReason.NONE && carEntityId == NO_ENTITY
                && effectiveRadius == 0 && carBattery == 0) {
            return Delta.NONE;
        }
        boolean closed = sessionMode != SeekerSessionMode.NONE;
        closeSessionFields();
        resetCar(SeekerCarState.NONE);
        cameraEntityId = NO_ENTITY;
        cooldownReason = SeekerCooldownReason.NONE;
        markTarget = null;
        markRemainingTicks = 0;
        carLostTo = null;
        pendingReturn = false;
        return new Delta(true, 0, SeekerCooldownReason.NONE, closed, null, false);
    }

    /** Server-only binding; never synced. / 仅服务端绑定，从不同步。 */
    public Delta bindMatch(String matchId) {
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
        buf.writeVarInt(cameraEntityId);
        buf.writeByte(sessionMode.id());
        buf.writeVarInt(sessionId);
        buf.writeByte(effectiveRadius);
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
        cameraEntityId = buf.readVarInt();
        sessionMode = SeekerSessionMode.fromId(buf.readUnsignedByte());
        sessionId = buf.readVarInt();
        effectiveRadius = buf.readUnsignedByte();
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
        cameraEntityId = NO_ENTITY;
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
        return "SeekerState{car=" + carState + ", carId=" + carEntityId + ", cameraId=" + cameraEntityId
                + ", session=" + sessionMode + "#" + sessionId + ", battery=" + carBattery
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
        effectiveRadius = 0;
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
