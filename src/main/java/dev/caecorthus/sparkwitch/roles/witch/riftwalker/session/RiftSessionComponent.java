package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUser;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stable CCA contract {@code sparkwitch:rift_session} (player, NEVER_COPY): the "inside a Rift Gate" session and the
 * re-entry cooldown. Server-authoritative; synced only to the owner, with remaining ticks (never absolute server
 * ticks); never persisted (a reload or relog starts clean) and bound to the match id. It never enters the shared
 * {@code sparkwitch:player} schema and is not an item or skill cooldown, so Fast Hands and forced-cooldown auras never
 * touch it (plan §7). Getters and the sync codec are frozen by G0; the transitions (setters) belong to P2.
 * 稳定 CCA 契约 {@code sparkwitch:rift_session}（玩家，NEVER_COPY）：「在裂隙门内」会话与再次进门冷却。服务端权威；
 * 仅同步给拥有者，且只发送剩余 tick（从不发送服务端绝对 tick）；从不持久化（重载或重连后为空），并绑定对局 id。
 * 从不进入共享的 {@code sparkwitch:player} 结构，也不是物品或技能冷却，因此快手与强制冷却光环都不会影响它（plan §7）。
 * Getter 与同步编码由 G0 冻结；状态转移（setter）归 P2。
 */
public final class RiftSessionComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<RiftSessionComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("rift_session"), RiftSessionComponent.class);

    private final PlayerEntity player;

    // Synced to the owner. / 同步给拥有者。
    private boolean inside;
    private int sessionId;
    private int gateNumber;
    private int ringIndex;
    private int ringSize;
    private int stayLimitTicks;

    // Server only. / 仅服务端。
    private long stayDeadlineTick;
    private long readyAtTick;
    @Nullable
    private Vec3d entryOrigin;
    private long nextHopTick;
    @Nullable
    private GameMode previousMode;
    @Nullable
    private String matchId;
    // P2 server-only session bookkeeping (never synced). / P2 仅服务端会话记录（从不同步）。
    @Nullable
    private RiftGateUser sessionUser;
    @Nullable
    private Vec3d anchor;
    @Nullable
    private Direction anchorFacing;
    @Nullable
    private RegistryKey<World> sessionWorld;
    private boolean foreignMove;
    private int lastSyncedStaySeconds;

    // Client mirrors, counted down locally between syncs. / 客户端镜像，两次同步之间本地倒计时。
    private int clientStayRemainingTicks;
    private int clientCooldownRemainingTicks;

    public RiftSessionComponent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity player() {
        return player;
    }

    /** Both sides: inside a gate right now. / 双端：当前是否在门内。 */
    public boolean inside() {
        return inside;
    }

    /** Both sides: server-issued id of the current session (0 when not inside); C2S payloads echo it. / 当前会话 id。 */
    public int sessionId() {
        return sessionId;
    }

    /** Both sides: {@code RiftGateRecord.number} of the current gate (0 when not inside). / 当前所在门的编号。 */
    public int gateNumber() {
        return gateNumber;
    }

    /** Both sides: 1-based position of the current gate in the hop ring, the "2" of "2/4" (0 when not inside). / 「2/4」中的 2。 */
    public int ringIndex() {
        return ringIndex;
    }

    /** Both sides: gates in the hop ring, the "4" of "2/4" (0 when not inside). / 「2/4」中的 4。 */
    public int ringSize() {
        return ringSize;
    }

    /** Both sides: this session's stay limit in ticks (D5b). / 本次会话的停留上限 tick（D5b）。 */
    public int stayLimitTicks() {
        return stayLimitTicks;
    }

    /** Remaining stay: live on the server, last synced countdown on the client. / 剩余停留：服务端实时，客户端为同步后的倒计时。 */
    public int stayRemainingTicks() {
        if (player.getWorld().isClient()) {
            return clientStayRemainingTicks;
        }
        return inside ? remainingUntil(stayDeadlineTick) : 0;
    }

    /** Remaining re-entry cooldown (D14): live on the server, synced countdown on the client. / 剩余再次进门冷却。 */
    public int cooldownRemainingTicks() {
        if (player.getWorld().isClient()) {
            return clientCooldownRemainingTicks;
        }
        return remainingUntil(readyAtTick);
    }

    /** Server only: world time at which the stay limit expires. / 仅服务端：停留上限到期的世界时间。 */
    public long stayDeadlineTick() {
        return stayDeadlineTick;
    }

    /** Server only: world time from which re-entry is allowed. / 仅服务端：允许再次进门的世界时间。 */
    public long readyAtTick() {
        return readyAtTick;
    }

    /** Server only: where the body stood before entering (last-resort exit). / 仅服务端：进门前的位置（最后的出门落点）。 */
    @Nullable
    public Vec3d entryOrigin() {
        return entryOrigin;
    }

    /** Server only: world time of the next allowed hop (D11 throttle). / 仅服务端：下一次允许跳门的世界时间（D11 节流）。 */
    public long nextHopTick() {
        return nextHopTick;
    }

    /** Server only: game mode to restore on a living exit (normally ADVENTURE). / 仅服务端：存活出门时恢复的游戏模式。 */
    @Nullable
    public GameMode previousMode() {
        return previousMode;
    }

    /** Server only: match the session/cooldown belongs to. / 仅服务端：会话与冷却所属的对局。 */
    @Nullable
    public String matchId() {
        return matchId;
    }

    /** Server only: the user class the session started with (D5b stay, D14 cooldown). / 仅服务端：进门时的使用者类别。 */
    @Nullable
    public RiftGateUser sessionUser() {
        return sessionUser;
    }

    /** Server only: feet position held while inside (current gate base centre). / 仅服务端：门内锚点（当前门底部中心）。 */
    @Nullable
    public Vec3d anchor() {
        return anchor;
    }

    /** Server only: front of the current gate. / 仅服务端：当前门的正面朝向。 */
    @Nullable
    public Direction anchorFacing() {
        return anchorFacing;
    }

    /** Server only: world the session lives in. / 仅服务端：会话所在世界。 */
    @Nullable
    public RegistryKey<World> sessionWorld() {
        return sessionWorld;
    }

    /** Server only: a foreign server teleport touched the body since the last tick. / 仅服务端：上次逐刻后是否有外部传送。 */
    public boolean foreignMove() {
        return foreignMove;
    }

    // ---- P2 transitions (server only; each syncs the owner when the synced view changed) ----
    // ---- P2 状态转移（仅服务端；同步视图变化时同步给拥有者） ----

    /**
     * Server: opens a session at a gate. Clears any running cooldown (it already allowed entry) and binds the match.
     * 服务端：在某扇门处开启会话。清除仍在计时的冷却（它已允许进门）并绑定对局。
     */
    public void beginSession(int sessionId, RiftGateUser user, int gateNumber, int ringIndex, int ringSize,
                             Vec3d anchor, Direction facing, RegistryKey<World> world, Vec3d entryOrigin,
                             GameMode previousMode, long stayDeadlineTick, int stayLimitTicks, String matchId) {
        this.inside = true;
        this.sessionId = sessionId;
        this.sessionUser = user;
        this.gateNumber = gateNumber;
        this.ringIndex = ringIndex;
        this.ringSize = ringSize;
        this.anchor = anchor;
        this.anchorFacing = facing;
        this.sessionWorld = world;
        this.entryOrigin = entryOrigin;
        this.previousMode = previousMode;
        this.stayDeadlineTick = stayDeadlineTick;
        this.stayLimitTicks = stayLimitTicks;
        this.readyAtTick = 0L;
        this.nextHopTick = 0L;
        this.matchId = matchId;
        this.foreignMove = false;
        this.lastSyncedStaySeconds = RiftSessionRules.secondsCeil(stayRemainingTicks());
        syncOwner();
    }

    /** Server: the anchor moved to another gate (a hop). / 服务端：锚点移到另一扇门（跳门）。 */
    public void moveToGate(int gateNumber, int ringIndex, int ringSize, Vec3d anchor, Direction facing,
                           long nextHopTick) {
        this.gateNumber = gateNumber;
        this.ringIndex = ringIndex;
        this.ringSize = ringSize;
        this.anchor = anchor;
        this.anchorFacing = facing;
        this.nextHopTick = nextHopTick;
        this.foreignMove = false;
        syncOwner();
    }

    /** Server: refreshes the "n/m" ring view; syncs only on change. / 服务端：刷新「n/m」；仅在变化时同步。 */
    public boolean updateRing(int ringIndex, int ringSize) {
        if (this.ringIndex == ringIndex && this.ringSize == ringSize) {
            return false;
        }
        this.ringIndex = ringIndex;
        this.ringSize = ringSize;
        syncOwner();
        return true;
    }

    /** Server: records a foreign server teleport of the body (read by the next tick). / 服务端：记录外部传送。 */
    public void markForeignMove() {
        this.foreignMove = true;
    }

    /** Server: forgets a foreign teleport that stayed within tolerance. / 服务端：忘记容差内的外部传送。 */
    public void clearForeignMove() {
        this.foreignMove = false;
    }

    /**
     * Server: resyncs the stay countdown once per displayed second, not every tick. / 服务端：每个显示秒同步一次停留倒计时。
     */
    public boolean syncStaySecondsIfChanged() {
        int seconds = RiftSessionRules.secondsCeil(stayRemainingTicks());
        if (!inside || seconds == lastSyncedStaySeconds) {
            return false;
        }
        lastSyncedStaySeconds = seconds;
        syncOwner();
        return true;
    }

    /**
     * Server: ends the session; {@code readyAtTick} is the next allowed entry (0 = no cooldown). The match binding stays
     * so the cooldown is dropped if another match starts.
     * 服务端：结束会话；{@code readyAtTick} 为下次允许进门的时间（0 表示无冷却）。保留对局绑定，以便新对局开始时丢弃该冷却。
     */
    public void endSession(long readyAtTick) {
        endSessionWithoutSync(readyAtTick);
        syncOwner();
    }

    /**
     * Server: {@link #endSession} without the owner sync. The caller MUST call {@link #syncOwner()} right after
     * restoring the game mode: the client must never see {@code inside=false} while it is still a spectator.
     * 服务端：不同步拥有者的 {@link #endSession}。调用方必须在恢复游戏模式后立即调用 {@link #syncOwner()}：客户端绝不能在
     * 仍是旁观者时看到 {@code inside=false}。
     */
    public void endSessionWithoutSync(long readyAtTick) {
        String keptMatch = matchId;
        resetFields();
        this.readyAtTick = Math.max(0L, readyAtTick);
        this.matchId = readyAtTick > 0L ? keptMatch : null;
    }

    /** Server: drops every field (session and cooldown) and syncs when anything changed. / 服务端：清空所有字段并在有变化时同步。 */
    public boolean clear() {
        boolean changed = inside || sessionId != 0 || gateNumber != 0 || ringIndex != 0 || ringSize != 0
                || stayLimitTicks != 0 || stayDeadlineTick != 0L || readyAtTick != 0L || entryOrigin != null
                || nextHopTick != 0L || previousMode != null || matchId != null
                || sessionUser != null || anchor != null || anchorFacing != null || sessionWorld != null
                || foreignMove || lastSyncedStaySeconds != 0
                || clientStayRemainingTicks != 0 || clientCooldownRemainingTicks != 0;
        resetFields();
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    public void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }

    private void resetFields() {
        inside = false;
        sessionId = 0;
        gateNumber = 0;
        ringIndex = 0;
        ringSize = 0;
        stayLimitTicks = 0;
        stayDeadlineTick = 0L;
        readyAtTick = 0L;
        entryOrigin = null;
        nextHopTick = 0L;
        previousMode = null;
        matchId = null;
        sessionUser = null;
        anchor = null;
        anchorFacing = null;
        sessionWorld = null;
        foreignMove = false;
        lastSyncedStaySeconds = 0;
        clientStayRemainingTicks = 0;
        clientCooldownRemainingTicks = 0;
    }

    private int remainingUntil(long tick) {
        long remaining = tick - player.getWorld().getTime();
        return (int) Math.clamp(remaining, 0L, Integer.MAX_VALUE);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            RiftSessionService.tick(serverPlayer);
        }
    }

    @Override
    public void clientTick() {
        if (clientStayRemainingTicks > 0) {
            clientStayRemainingTicks--;
        }
        if (clientCooldownRemainingTicks > 0) {
            clientCooldownRemainingTicks--;
        }
    }

    /**
     * Frozen owner-only codec: inside (boolean), then varints sessionId, gateNumber, ringIndex, ringSize,
     * stayRemainingTicks, stayLimitTicks, cooldownRemainingTicks. Remaining values are computed at write time.
     * 冻结的仅拥有者编码：inside（布尔），随后依次为 varint sessionId、gateNumber、ringIndex、ringSize、stayRemainingTicks、
     * stayLimitTicks、cooldownRemainingTicks。剩余值在写入时计算。
     */
    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(inside);
        buf.writeVarInt(sessionId);
        buf.writeVarInt(gateNumber);
        buf.writeVarInt(ringIndex);
        buf.writeVarInt(ringSize);
        buf.writeVarInt(stayRemainingTicks());
        buf.writeVarInt(stayLimitTicks);
        buf.writeVarInt(cooldownRemainingTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        inside = buf.readBoolean();
        sessionId = buf.readVarInt();
        gateNumber = buf.readVarInt();
        ringIndex = buf.readVarInt();
        ringSize = buf.readVarInt();
        int stayRemaining = buf.readVarInt();
        stayLimitTicks = Math.max(0, buf.readVarInt());
        int cooldownRemaining = buf.readVarInt();
        clientStayRemainingTicks = inside ? Math.max(0, stayRemaining) : 0;
        clientCooldownRemainingTicks = Math.max(0, cooldownRemaining);
    }

    /** Never persisted. / 从不持久化。 */
    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    /** A load always starts clean (transient, match-bound). / 加载时总是从空状态开始（瞬态、绑定对局）。 */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        resetFields();
    }
}
