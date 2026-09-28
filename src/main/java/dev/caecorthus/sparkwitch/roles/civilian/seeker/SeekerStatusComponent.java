package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceSounds;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerSessionState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie.SeekerTaotieService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;
import java.util.UUID;

/**
 * Stable CCA contract {@code sparkwitch:seeker_status} (NEVER_COPY): the Seeker's own car, camera, session, battery
 * and mark state. Server-authoritative; synced only to the owner as remaining ticks, never absolute server ticks. It
 * never enters the shared {@code sparkwitch:player} schema.
 * 稳定 CCA 契约 {@code sparkwitch:seeker_status}（NEVER_COPY）：搜寻者自己的小车、摄像头、会话、电量与标记状态。
 * 由服务端权威决定，仅以剩余刻数同步给拥有者，从不发送绝对服务端刻；从不进入共享的 {@code sparkwitch:player} 结构。
 */
public final class SeekerStatusComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SeekerStatusComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("seeker_status"), SeekerStatusComponent.class);
    static final String BATTERY_LOW_MESSAGE_KEY = "message.sparkwitch.seeker.car.battery_low";
    /** Taotie return poll / retry period and device-existence check period. / 饕餮归还轮询与设备存在性检查周期。 */
    static final int SLOW_POLL_TICKS = 20;
    private static final int EXISTENCE_POLL_OFFSET = 10;

    private final PlayerEntity player;
    private final SeekerState state = new SeekerState();
    @Nullable
    private SeekerSessionState sessionState;

    public SeekerStatusComponent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity player() {
        return player;
    }

    /** Mutable pure state; mutate only through transitions, then {@link #apply}. / 仅通过状态转移修改，然后调用 apply。 */
    public SeekerState state() {
        return state;
    }

    public SeekerCarState carState() {
        return state.carState();
    }

    public int carEntityId() {
        return state.carEntityId();
    }

    /** The owner's cameras in label order. / 按编号排列的拥有者摄像头。 */
    public List<SeekerState.Camera> cameras() {
        return state.cameras();
    }

    public int cameraCount() {
        return state.cameraCount();
    }

    public boolean hasCamera(int entityId) {
        return state.hasCamera(entityId);
    }

    public SeekerSessionMode sessionMode() {
        return state.sessionMode();
    }

    public int sessionId() {
        return state.sessionId();
    }

    public int effectiveRadius() {
        return state.effectiveRadius();
    }

    public SeekerCooldownReason cooldownReason() {
        return state.cooldownReason();
    }

    @Nullable
    public UUID markTarget() {
        return state.markTarget();
    }

    public int markRemainingTicks() {
        return state.markRemainingTicks();
    }

    public int carBattery() {
        return state.carBattery();
    }

    @Nullable
    public UUID carLostTo() {
        return state.carLostTo();
    }

    public boolean pendingReturn() {
        return state.pendingReturn();
    }

    /** WP-09's opaque server-only session data; never synced or saved. / WP-09 独有的仅服务端会话数据，不同步也不存盘。 */
    @Nullable
    public SeekerSessionState sessionState() {
        return sessionState;
    }

    public void setSessionState(@Nullable SeekerSessionState sessionState) {
        this.sessionState = sessionState;
    }

    /**
     * Server only, the single sink of every transition result: writes {@code delta.cooldownTicks()} with
     * {@code SeekerCooldowns.writeFloorExact(owner, ticks, reason)} when positive (callers never write car cooldowns
     * themselves) and syncs the owner when dirty. It never ends sessions, sends messages or calls device services:
     * session endings go through {@code SeekerRemoteSessionService.end} first, and {@code batteryDepleted} is handled by
     * the component's own tick ({@code SeekerDeviceService.depleteCar}).
     * 仅服务端，所有状态转移结果的唯一收口：{@code delta.cooldownTicks()} 为正时经
     * {@code SeekerCooldowns.writeFloorExact} 写入（调用方从不自行写小车冷却），delta 有变化时同步给拥有者。
     * 它从不结束会话、发送消息或调用设备服务：会话结束先经 {@code SeekerRemoteSessionService.end}，
     * {@code batteryDepleted} 由组件自己的刻处理（{@code SeekerDeviceService.depleteCar}）。
     */
    public void apply(SeekerState.Delta delta) {
        if (applyQuietly(delta)) {
            syncOwner();
        }
    }

    /** Server-only match binding (never synced). / 仅服务端的对局绑定（从不同步）。 */
    public void bindMatch(String matchId) {
        apply(state.bindMatch(matchId));
    }

    /**
     * Drops every Seeker field (WP-09's session data too) and syncs. Devices and session endings are the caller's:
     * the lifecycle ends the session and discards the devices first.
     * 清除所有搜寻者字段（含 WP-09 的会话数据）并同步。设备与会话结束由调用方负责：生命周期会先结束会话并移除设备。
     */
    public void clearAll() {
        sessionState = null;
        apply(state.clear());
    }

    /** Server only; the owner is the only recipient. / 仅服务端；拥有者是唯一接收者。 */
    public void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }

    /**
     * Server tick order (WP-02): owner-of-record self-heal → final-death fallback →
     * {@code SeekerRemoteSessionService.tick} (drop a stale {@code SeekerSessionState} when the mode is NONE is WP-09's)
     * → device existence ({@code loseCar}) → battery ({@code tickBattery}; on {@code batteryDepleted} call
     * {@code SeekerDeviceService.depleteCar}; on first crossing ≤20% / ≤10% send
     * {@code message.sparkwitch.seeker.car.battery_low} and {@code SeekerDeviceSounds.playBatteryLow}) →
     * every 20 ticks while SWALLOWED or PendingReturn: {@code SeekerTaotieService.tick} → mark decay → sync if dirty.
     * 服务端刻顺序（WP-02）：记录拥有者自愈 → 最终死亡兜底 → 会话刻 → 设备存在性 → 电量（耗尽时调用 depleteCar；
     * 首次降至 ≤20% / ≤10% 时发送电量不足提示并播放警告音）→ SWALLOWED 或 PendingReturn 时每 20 刻调用饕餮刻 →
     * 标记衰减 → 有变化时同步。
     */
    @Override
    public void serverTick() {
        // Ticks for every player: non-Seekers hold no state and leave here after a few field reads.
        // 每位玩家都会执行：非搜寻者不持有状态，仅读取几个字段后即在此返回。
        if (sessionState == null && state.isIdle()) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        // 1. Owner-of-record self-heal: role lost, round over or a stale match drops everything.
        // 1. 记录拥有者自愈：失去职业、对局结束或对局过期时清除一切。
        if (!SeekerTargeting.isOwnerOfRecord(owner)) {
            SeekerLifecycleService.cleanUp(owner, selfHealReason(owner));
            return;
        }
        // 2. Final-death fallback; a Last Stand pending death is an alive-spectator state, not a final death.
        // 2. 最终死亡兜底；背水一战待定属于活着的旁观状态，而非最终死亡。
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        if (game.isPlayerDead(owner.getUuid()) && !SparkTraitsSeekerBridge.isLastStandPending(owner)) {
            SeekerLifecycleService.cleanUp(owner, SeekerExitReason.DIED);
            return;
        }
        // Round over (STOPPING fade): no view survives it, also in modes or /stop paths without ON_WIN_DETERMINED.
        // 回合已结束（STOPPING 淡出）：任何视角都不保留，包括不触发 ON_WIN_DETERMINED 的模式或 /stop 路径。
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                && state.sessionMode() != SeekerSessionMode.NONE) {
            SeekerRemoteSessionService.end(owner, SeekerExitReason.ROUND_END);
        }
        // 3. Session checks (WP-09 owns every per-tick exit reason and stale SeekerSessionState).
        // 3. 会话检查（WP-09 负责每刻的所有退出原因以及过期的会话状态）。
        SeekerRemoteSessionService.tick(owner, this);
        long time = owner.getServerWorld().getTime();
        boolean dirty = false;
        // 4. Device existence: a vanished car is never a free reset (RECALL cooldown); each vanished camera is dropped.
        // 4. 设备存在性：丢失的小车绝不免费重置（RECALL 冷却）；每个丢失的摄像头引用都被移除。
        if (time % SLOW_POLL_TICKS == EXISTENCE_POLL_OFFSET) {
            dirty |= checkDevicesExist(owner);
        }
        // 5. Battery (server-authoritative; the client only displays the synced percent).
        // 5. 电量（服务端权威；客户端只显示同步的百分比）。
        if (state.carState() == SeekerCarState.DEPLOYED) {
            SeekerState.Delta battery = state.tickBattery();
            dirty |= applyQuietly(battery);
            if (battery.batteryDepleted()) {
                if (dirty) {
                    syncOwner();
                    dirty = false;
                }
                SeekerDeviceService.depleteCar(owner);
            } else {
                int warning = state.pollBatteryWarning();
                if (warning >= 0) {
                    owner.sendMessage(Text.translatable(BATTERY_LOW_MESSAGE_KEY, warning), true);
                    SeekerDeviceSounds.playBatteryLow(owner);
                }
            }
        }
        // 6. Taotie return poll and PendingReturn retry, both owned by WP-06.
        // 6. 饕餮归还轮询与 PendingReturn 重试，均由 WP-06 负责。
        if ((state.carState() == SeekerCarState.SWALLOWED || state.pendingReturn()) && time % SLOW_POLL_TICKS == 0) {
            SeekerTaotieService.tick(owner, this);
        }
        // 7. Mark decay.
        // 7. 标记衰减。
        dirty |= applyQuietly(state.tickMark());
        // 8. Sync once when anything this tick changed a synced field.
        // 8. 本刻有同步字段变化时统一同步一次。
        if (dirty) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        state.clientTick();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        state.writeSync(buf);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.readSync(buf);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.writeNbt(tag);
    }

    /** Resets every transient field, including WP-09's session data. / 重置所有瞬态字段，包括 WP-09 的会话数据。 */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.readNbt(tag);
        sessionState = null;
    }

    /**
     * Writes the delta's cooldown (the single cooldown writer) and reports whether a synced field changed.
     * 写入 delta 的冷却（唯一的冷却写入方），并返回同步字段是否有变化。
     */
    private boolean applyQuietly(SeekerState.Delta delta) {
        if (delta == null || player.getWorld().isClient()) {
            return false;
        }
        if (delta.cooldownTicks() > 0 && player instanceof ServerPlayerEntity owner) {
            SeekerCooldowns.writeFloorExact(owner, delta.cooldownTicks(), delta.cooldownReason());
        }
        return delta.dirty();
    }

    private boolean checkDevicesExist(ServerPlayerEntity owner) {
        boolean dirty = false;
        if (state.carState() == SeekerCarState.DEPLOYED && SeekerDeviceService.findCar(owner) == null) {
            SparkWitch.LOGGER.warn("Seeker car of {} vanished while deployed; applying the recall cooldown",
                    owner.getGameProfile().getName());
            if (state.sessionMode() == SeekerSessionMode.CAR) {
                SeekerRemoteSessionService.end(owner, SeekerExitReason.FOCUS_LOST);
            }
            dirty |= applyQuietly(state.loseCar());
        }
        // Copy first: destroyCamera mutates the list. / 先复制：destroyCamera 会修改列表。
        for (SeekerState.Camera camera : List.copyOf(state.cameras())) {
            if (SeekerDeviceService.findCamera(owner, camera.entityId()) != null) {
                continue;
            }
            if (state.sessionMode() == SeekerSessionMode.CAMERA
                    && state.sessionFocusEntityId() == camera.entityId()) {
                SeekerRemoteSessionService.end(owner, SeekerExitReason.FOCUS_LOST);
            }
            dirty |= applyQuietly(state.destroyCamera(camera.entityId()));
        }
        return dirty;
    }

    private static SeekerExitReason selfHealReason(ServerPlayerEntity owner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        return game.isRunning() && !SeekerRules.isSeeker(game.getRole(owner))
                ? SeekerExitReason.ROLE_CHANGED
                : SeekerExitReason.ROUND_END;
    }
}
