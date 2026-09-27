package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Frozen contract: the pure Seeker state machine (car, camera, session, battery, mark). Transitions mutate this object
 * and return a {@link Delta}; the owning service applies cooldowns and side effects, the component syncs.
 * TODO(WP-02): implement every transition, the owner sync codec and NBT (see the contract sheet for field order).
 * 待 WP-02 实现所有状态转移、拥有者同步编解码与 NBT（字段顺序见契约文档）。
 * 冻结契约：纯搜寻者状态机（小车、摄像头、会话、电量、标记）。状态转移会修改本对象并返回 {@link Delta}；
 * 由负责的服务执行冷却与副作用，由组件同步。
 */
public final class SeekerState {
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

    public SeekerCarState carState() {
        return SeekerCarState.NONE;
    }

    public int carEntityId() {
        return -1;
    }

    public int cameraEntityId() {
        return -1;
    }

    public SeekerSessionMode sessionMode() {
        return SeekerSessionMode.NONE;
    }

    public int sessionId() {
        return 0;
    }

    public int effectiveRadius() {
        return 0;
    }

    public SeekerCooldownReason cooldownReason() {
        return SeekerCooldownReason.NONE;
    }

    @Nullable
    public UUID markTarget() {
        return null;
    }

    public int markRemainingTicks() {
        return 0;
    }

    /** 0..100; meaningful only while DEPLOYED. / 0..100；仅在 DEPLOYED 时有意义。 */
    public int carBattery() {
        return 0;
    }

    @Nullable
    public UUID carLostTo() {
        return null;
    }

    public boolean pendingReturn() {
        return false;
    }

    @Nullable
    public String matchId() {
        return null;
    }

    /** NONE → READY at the round-start grant (+INITIAL). / 开局发放：NONE → READY（+INITIAL）。 */
    public Delta grant() {
        return Delta.NONE;
    }

    /** READY → DEPLOYED with a full battery. / READY → DEPLOYED，电量充满。 */
    public Delta deploy(int carEntityId) {
        return Delta.NONE;
    }

    /** DEPLOYED → READY (+RECALL 180 s); closes a CAR session with CAR_RECALLED. / 回收。 */
    public Delta recall() {
        return Delta.NONE;
    }

    /** DEPLOYED → READY (+BROKEN 180 s); closes a CAR session with CAR_BROKEN. / 损坏。 */
    public Delta breakCar() {
        return Delta.NONE;
    }

    /** DEPLOYED → READY (+DEPLETED 180 s); closes a CAR session with BATTERY_DEPLETED. / 电量耗尽。 */
    public Delta deplete() {
        return Delta.NONE;
    }

    /** DEPLOYED entity vanished unexpectedly → READY (+RECALL), never a free reset. / 实体异常丢失。 */
    public Delta loseCar() {
        return Delta.NONE;
    }

    /** DEPLOYED → SWALLOWED, LostTo = taotie; no cooldown. / 被饕餮吞噬。 */
    public Delta swallow(UUID taotie) {
        return Delta.NONE;
    }

    /** SWALLOWED → READY (+RETURNED 60 s). / 归还。 */
    public Delta returnCar() {
        return Delta.NONE;
    }

    public Delta setPendingReturn(boolean pending) {
        return Delta.NONE;
    }

    public Delta placeCamera(int cameraEntityId) {
        return Delta.NONE;
    }

    /** Closes a CAMERA session with CAMERA_BROKEN. / 结束摄像头会话。 */
    public Delta destroyCamera() {
        return Delta.NONE;
    }

    /** Requires no session or a mode switch; increments sessionId. / 要求当前无会话或为模式切换；sessionId 递增。 */
    public Delta openSession(SeekerSessionMode mode, int focusEntityId, int effectiveRadius) {
        return Delta.NONE;
    }

    /** Idempotent. / 幂等。 */
    public Delta closeSession(SeekerExitReason reason) {
        return Delta.NONE;
    }

    /**
     * One server tick of battery drain while DEPLOYED: 1% per 10 ticks when the session mode is CAR, else 1% per
     * 60 ticks; reports {@code batteryDepleted} once when it reaches 0.
     * 部署期间每个服务端刻的电量消耗：会话模式为 CAR 时每 10 刻 1%，否则每 60 刻 1%；归零时报告一次 {@code batteryDepleted}。
     */
    public Delta tickBattery() {
        return Delta.NONE;
    }

    /** Newest mark replaces the previous one. / 新标记替换旧标记。 */
    public Delta mark(UUID target, int ticks) {
        return Delta.NONE;
    }

    public Delta tickMark() {
        return Delta.NONE;
    }

    public Delta clearMark() {
        return Delta.NONE;
    }

    /** Any → NONE: final death, role change, reset, finalize. / 任意 → NONE。 */
    public Delta clear() {
        return Delta.NONE;
    }

    public Delta bindMatch(String matchId) {
        return Delta.NONE;
    }

    /** Client-side local countdown between syncs. / 两次同步之间的客户端本地倒计时。 */
    public void clientTick() {
    }

    public void writeSync(RegistryByteBuf buf) {
    }

    public void readSync(RegistryByteBuf buf) {
    }

    public void writeNbt(NbtCompound nbt) {
    }

    public void readNbt(NbtCompound nbt) {
    }
}
