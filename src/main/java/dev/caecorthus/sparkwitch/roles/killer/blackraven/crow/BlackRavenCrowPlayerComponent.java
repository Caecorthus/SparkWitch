package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMatch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Raven Form state ({@code sparkwitch:black_raven_crow}, {@code NEVER_COPY}, appended last), bound to the current Wathe
 * match. Sync contract: EVERY client receives the {@link #isActive()} bit, because other clients must hide the body
 * (name tag, model and held items, Blind echoes, instinct) and exempt it from their own local collision prediction
 * (the SparkFactionAPI exemption runs on each client); the RAW role is already public through Wathe's world sync, so the
 * bit leaks nothing new. Everything else (tasks, cooldown, crow entity id, session ticks, the Homing Feather marker and
 * the landing hold reserved for crow-world) goes to the owner only. Server-only: the match binding, the body anchor,
 * the ungrounded counter and the invisibility ownership. Only tasks, cooldown and the match binding are saved; a
 * session never survives a reload.
 * 化鸦状态（{@code sparkwitch:black_raven_crow}，{@code NEVER_COPY}，追加在最后），绑定当前 Wathe 对局。同步契约：
 * 所有客户端都接收 {@link #isActive()} 标记，因为其他客户端需要隐藏本体（名牌、模型与手持物品、盲人回声、本能），并在本地碰撞预测中
 * 豁免它（SparkFactionAPI 豁免在各客户端上执行）；原始职业已通过 Wathe 世界同步公开，此标记不泄露新信息。其余字段（任务数、冷却、
 * 乌鸦实体 id、会话刻、为 crow-world 预留的归羽标记与落地定身）只同步给本人。仅服务端：对局绑定、本体锚点、离地计数与隐身归属。
 * 只保存任务数、冷却与对局绑定；会话从不跨越重载。
 */
public final class BlackRavenCrowPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<BlackRavenCrowPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            BlackRavenCrowRules.COMPONENT_ID, BlackRavenCrowPlayerComponent.class);

    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int NO_ENTITY = -1;

    private final PlayerEntity player;
    // Saved. / 存盘。
    private @Nullable UUID matchId;
    private int completedTasks;
    private int cooldownTicks;
    // Session, synced (active to everyone, the rest to the owner). / 会话，同步（active 给所有人，其余给本人）。
    private boolean active;
    private int crowEntityId = NO_ENTITY;
    private long sessionStartTick;
    private long sessionEndTick;
    // Reserved for crow-world, owner-synced. / 为 crow-world 预留，同步给本人。
    private boolean featherDropped;
    private Vec3d featherPos = Vec3d.ZERO;
    private int featherEntityId = NO_ENTITY;
    private long landingHoldUntilTick;
    // Server-only session bookkeeping. / 仅服务端的会话记录。
    private Vec3d anchor = Vec3d.ZERO;
    private int ungroundedTicks;
    private boolean ownsInvisibility;

    public BlackRavenCrowPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    // ---- Unlock / 解锁 ----

    public int getCompletedTasks() {
        return completedTasks;
    }

    public boolean isUnlocked() {
        return BlackRavenCrowRules.isUnlocked(completedTasks);
    }

    /**
     * Server: counts one of the owner's own tasks in {@code currentMatch} (a different binding starts over); true on
     * the task that unlocks Raven Form.
     * 服务端：在 {@code currentMatch} 中记一个本人完成的任务（绑定不同则从头开始）；解锁的那个任务返回 true。
     */
    public boolean recordCompletedTask(@Nullable UUID currentMatch) {
        if (currentMatch != null && !currentMatch.equals(matchId)) {
            clear();
            matchId = currentMatch;
        }
        int before = completedTasks;
        completedTasks = Math.min(BlackRavenCrowRules.UNLOCK_TASKS, completedTasks + 1);
        if (completedTasks != before) {
            sync();
        }
        return BlackRavenCrowRules.unlocksOn(before, completedTasks);
    }

    public @Nullable UUID getMatchId() {
        return matchId;
    }

    // ---- Cooldown / 冷却 ----

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** Only ever lengthens the cooldown, then resyncs. / 只会延长冷却，并重新同步。 */
    public void raiseCooldown(int ticks) {
        if (ticks > cooldownTicks) {
            cooldownTicks = ticks;
            sync();
        }
    }

    /** Sets the cooldown exactly (forced-cooldown store). / 精确设定冷却（强制冷却存储使用）。 */
    public void setCooldown(int ticks) {
        int normalized = Math.max(0, ticks);
        if (normalized != cooldownTicks) {
            cooldownTicks = normalized;
            sync();
        }
    }

    // ---- Session / 会话 ----

    /** Side-neutral: true on every client while the owner is in Raven Form. / 两端通用：主人化鸦期间在所有客户端上为 true。 */
    public boolean isActive() {
        return active;
    }

    /** Owner and server only; -1 without a session. / 仅本人与服务端；无会话时为 -1。 */
    public int getCrowEntityId() {
        return crowEntityId;
    }

    /** World time the session started (owner and server). / 会话开始的世界时间（本人与服务端）。 */
    public long getSessionStartTick() {
        return sessionStartTick;
    }

    /** World time the session times out (owner and server). / 会话超时的世界时间（本人与服务端）。 */
    public long getSessionEndTick() {
        return sessionEndTick;
    }

    /** Server only: the frozen body's anchor. / 仅服务端：冻结本体的锚点。 */
    public Vec3d getAnchor() {
        return anchor;
    }

    boolean ownsInvisibility() {
        return ownsInvisibility;
    }

    /** Server: the session now owns the body's invisibility. / 服务端：本体隐身改由本会话持有。 */
    void takeInvisibilityOwnership() {
        ownsInvisibility = true;
    }

    void beginSession(int crowId, long startTick, long endTick, Vec3d bodyAnchor, boolean ownsBodyInvisibility) {
        active = true;
        crowEntityId = crowId;
        sessionStartTick = startTick;
        sessionEndTick = endTick;
        anchor = bodyAnchor;
        ungroundedTicks = 0;
        ownsInvisibility = ownsBodyInvisibility;
        sync();
    }

    /** Clears the session fields only (tasks, cooldown and feather stay). / 只清除会话字段（任务、冷却与归羽保留）。 */
    void endSession() {
        if (!active && crowEntityId == NO_ENTITY) {
            return;
        }
        active = false;
        crowEntityId = NO_ENTITY;
        sessionStartTick = 0L;
        sessionEndTick = 0L;
        anchor = Vec3d.ZERO;
        ungroundedTicks = 0;
        ownsInvisibility = false;
        sync();
    }

    /** Server: counts an ungrounded tick; true once past the grace. / 服务端：记一刻离地；超过宽限后返回 true。 */
    boolean tickUngrounded(boolean grounded) {
        if (grounded) {
            ungroundedTicks = 0;
            return false;
        }
        return ++ungroundedTicks > BlackRavenCrowRules.UNGROUNDED_GRACE_TICKS;
    }

    // ---- Reserved for crow-world: Homing Feather marker and landing hold / 为 crow-world 预留 ----

    /** Whether a Homing Feather marker is out this session. / 本次会话是否已丢出归羽标记。 */
    public boolean hasFeather() {
        return featherDropped;
    }

    public Vec3d getFeatherPos() {
        return featherPos;
    }

    /** Marker entity id, or -1. / 标记实体 id，没有时为 -1。 */
    public int getFeatherEntityId() {
        return featherEntityId;
    }

    /** crow-world: records the dropped marker (owner-synced). / crow-world：记录已丢出的标记（同步给本人）。 */
    public void setFeather(Vec3d pos, int entityId) {
        featherDropped = true;
        featherPos = pos;
        featherEntityId = entityId;
        sync();
    }

    /** crow-world: the marker landed somewhere else (owner-synced). / crow-world：标记落到了别处（同步给本人）。 */
    public void updateFeatherPos(Vec3d pos) {
        if (featherDropped && !pos.equals(featherPos)) {
            featherPos = pos;
            sync();
        }
    }

    public void clearFeather() {
        if (!featherDropped && featherEntityId == NO_ENTITY) {
            return;
        }
        featherDropped = false;
        featherPos = Vec3d.ZERO;
        featherEntityId = NO_ENTITY;
        sync();
    }

    public long getLandingHoldUntilTick() {
        return landingHoldUntilTick;
    }

    /** Whether crow-world's 3 s landing hold is running at {@code now}. / {@code now} 时 crow-world 的 3 秒落地定身是否进行中。 */
    public boolean isLandingHeld(long now) {
        return now < landingHoldUntilTick;
    }

    /** crow-world: starts (or clears with 0) the landing hold (owner-synced). / crow-world：开始（传 0 则清除）落地定身。 */
    public void setLandingHoldUntil(long untilTick) {
        long normalized = Math.max(0L, untilTick);
        if (normalized != landingHoldUntilTick) {
            landingHoldUntilTick = normalized;
            sync();
        }
    }

    // ---- Lifecycle / 生命周期 ----

    boolean isIdle() {
        return matchId == null && completedTasks == 0 && cooldownTicks == 0 && !active
                && crowEntityId == NO_ENTITY && !featherDropped && landingHoldUntilTick == 0L;
    }

    /**
     * Wipes everything. Never call this with a live session: {@code BlackRavenCrowSessions.end} must restore the body
     * and remove the crow first.
     * 清空全部状态。会话进行中切勿直接调用：须先由 {@code BlackRavenCrowSessions.end} 恢复本体并移除乌鸦。
     */
    public void clear() {
        if (isIdle()) {
            return;
        }
        matchId = null;
        completedTasks = 0;
        cooldownTicks = 0;
        active = false;
        crowEntityId = NO_ENTITY;
        sessionStartTick = 0L;
        sessionEndTick = 0L;
        featherDropped = false;
        featherPos = Vec3d.ZERO;
        featherEntityId = NO_ENTITY;
        landingHoldUntilTick = 0L;
        anchor = Vec3d.ZERO;
        ungroundedTicks = 0;
        ownsInvisibility = false;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void serverTick() {
        if (isIdle() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        UUID current = BlackRavenMatch.currentId();
        if (!active && matchId != null && current != null && !matchId.equals(current)) {
            // A stale binding from another match (e.g. a rejoin with old NBT). / 来自其他对局的过期绑定（如带旧存档重进）。
            clear();
            return;
        }
        BlackRavenCrowSessions.tick(serverPlayer, this);
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks % SYNC_INTERVAL_TICKS == 0) {
                sync();
            }
        }
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone receives the active bit; the rest is written for the owner only below.
        // 所有人都接收在场标记；其余字段在下方只写给本人。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean owner = recipient == player;
        buf.writeBoolean(active);
        buf.writeBoolean(owner);
        if (!owner) {
            return;
        }
        buf.writeVarInt(completedTasks);
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(crowEntityId);
        buf.writeVarLong(sessionStartTick);
        buf.writeVarLong(sessionEndTick);
        buf.writeBoolean(featherDropped);
        buf.writeDouble(featherPos.x);
        buf.writeDouble(featherPos.y);
        buf.writeDouble(featherPos.z);
        buf.writeVarInt(featherEntityId);
        buf.writeVarLong(landingHoldUntilTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        active = buf.readBoolean();
        boolean owner = buf.readBoolean();
        if (!owner) {
            completedTasks = 0;
            cooldownTicks = 0;
            crowEntityId = NO_ENTITY;
            sessionStartTick = 0L;
            sessionEndTick = 0L;
            featherDropped = false;
            featherPos = Vec3d.ZERO;
            featherEntityId = NO_ENTITY;
            landingHoldUntilTick = 0L;
            return;
        }
        completedTasks = Math.max(0, buf.readVarInt());
        cooldownTicks = Math.max(0, buf.readVarInt());
        crowEntityId = buf.readVarInt();
        sessionStartTick = buf.readVarLong();
        sessionEndTick = buf.readVarLong();
        featherDropped = buf.readBoolean();
        featherPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        featherEntityId = buf.readVarInt();
        landingHoldUntilTick = buf.readVarLong();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (matchId != null) {
            tag.putUuid("MatchId", matchId);
        }
        tag.putInt("CompletedTasks", completedTasks);
        tag.putInt("Cooldown", cooldownTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        matchId = tag.containsUuid("MatchId") ? tag.getUuid("MatchId") : null;
        completedTasks = Math.clamp(tag.getInt("CompletedTasks"), 0, BlackRavenCrowRules.UNLOCK_TASKS);
        cooldownTicks = Math.max(0, tag.getInt("Cooldown"));
        // A session never survives a reload. / 会话从不跨越重载。
        active = false;
        crowEntityId = NO_ENTITY;
        sessionStartTick = 0L;
        sessionEndTick = 0L;
        featherDropped = false;
        featherPos = Vec3d.ZERO;
        featherEntityId = NO_ENTITY;
        landingHoldUntilTick = 0L;
        anchor = Vec3d.ZERO;
        ungroundedTicks = 0;
        ownsInvisibility = false;
    }
}
