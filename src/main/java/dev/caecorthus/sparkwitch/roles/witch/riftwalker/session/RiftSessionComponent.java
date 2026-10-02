package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
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

    // TODO(P2): session transitions (begin, hop, end, cooldown, match binding), each followed by syncOwner().
    // TODO(P2)：会话状态转移（开始、跳门、结束、冷却、对局绑定），每次之后调用 syncOwner()。

    /** Server: drops every field (session and cooldown) and syncs when anything changed. / 服务端：清空所有字段并在有变化时同步。 */
    public boolean clear() {
        boolean changed = inside || sessionId != 0 || gateNumber != 0 || ringIndex != 0 || ringSize != 0
                || stayLimitTicks != 0 || stayDeadlineTick != 0L || readyAtTick != 0L || entryOrigin != null
                || nextHopTick != 0L || previousMode != null || matchId != null
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
