package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Per-player, owner-private Bell Ringer state kept outside the shared {@code sparkwitch:player} schema:
 * the forced Echo task marker and its deadline, the red "heard the bell" hint window, and (for the
 * ringer only) whether the bell currently has a valid target. The server is authoritative; the owner's
 * client receives the Echo task type, the toll flag and remaining-tick counters (never absolute server
 * ticks), and decrements the counters locally until the next sync.
 * 每名玩家独立、仅拥有者可见的敲钟人状态，不进入共享的 {@code sparkwitch:player} 结构：
 * 强制回响任务标记及其截止时间、红色“听到钟声”提示窗口，以及（仅敲钟人）钟当前是否存在有效目标。
 * 服务端为权威；拥有者客户端接收回响任务类型、敲钟标记与剩余 tick 计数（从不接收服务端绝对 tick），
 * 并在下次同步前本地递减计数。
 */
public final class BellEchoPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<BellEchoPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("bell_echo"),
            BellEchoPlayerComponent.class
    );
    private static final int NO_TASK = -1;
    private static final PlayerMoodComponent.Task[] TASKS = PlayerMoodComponent.Task.values();

    private final PlayerEntity player;
    private @Nullable UUID matchId;
    private int echoTaskOrdinal = NO_TASK;
    /** Server world time at which an unfinished Echo task fails. / 未完成回响任务判定失败的服务端世界时间。 */
    private long echoDeadlineTick;
    /** Server world time at which the red hint expires. / 红色提示过期的服务端世界时间。 */
    private long heardUntilTick;
    /** Ringer-only glint flag; recomputed by the server, never persisted. / 仅敲钟人的发光标记，由服务端重算，不持久化。 */
    private boolean tollReady;
    private int clientEchoRemainingTicks;
    private int clientHeardRemainingTicks;

    public BellEchoPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean hasEchoTask() {
        return echoTask() != null;
    }

    public @Nullable PlayerMoodComponent.Task echoTask() {
        return echoTaskOrdinal >= 0 && echoTaskOrdinal < TASKS.length ? TASKS[echoTaskOrdinal] : null;
    }

    public boolean isEchoTask(@Nullable PlayerMoodComponent.Task type) {
        return type != null && type == echoTask();
    }

    public long echoDeadlineTick() {
        return echoDeadlineTick;
    }

    public long heardUntilTick() {
        return heardUntilTick;
    }

    public @Nullable UUID matchId() {
        return matchId;
    }

    /** True while any round-scoped state is held. / 持有任何本局状态时为 true。 */
    public boolean hasRoundState() {
        return matchId != null || echoTaskOrdinal != NO_TASK || echoDeadlineTick != 0L
                || heardUntilTick != 0L || tollReady;
    }

    /** Server: marks the forced Echo task; callers skip players who already have one. / 服务端：标记强制回响任务；已有任务者由调用方跳过。 */
    public void startEcho(PlayerMoodComponent.Task type, long deadline, @Nullable UUID match) {
        echoTaskOrdinal = type == null ? NO_TASK : type.ordinal();
        echoDeadlineTick = type == null ? 0L : Math.max(0L, deadline);
        matchId = match;
        syncOwner();
    }

    /** Server: drops only the Echo marker (never touches Wathe tasks). / 服务端：仅清除回响标记（不改动 Wathe 任务）。 */
    public boolean clearEcho() {
        if (echoTaskOrdinal == NO_TASK && echoDeadlineTick == 0L) {
            return false;
        }
        echoTaskOrdinal = NO_TASK;
        echoDeadlineTick = 0L;
        syncOwner();
        return true;
    }

    public void startHeard(long until, @Nullable UUID match) {
        heardUntilTick = Math.max(0L, until);
        matchId = match;
        syncOwner();
    }

    public boolean clearHeard() {
        if (heardUntilTick == 0L) {
            return false;
        }
        heardUntilTick = 0L;
        syncOwner();
        return true;
    }

    /** Server: syncs only on change. / 服务端：仅在变化时同步。 */
    public void setTollReady(boolean ready) {
        if (tollReady == ready) {
            return;
        }
        tollReady = ready;
        syncOwner();
    }

    /** Both sides: server value, or the owner's last synced value on the client. / 双端：服务端值，或客户端拥有者最近同步值。 */
    public boolean tollReady() {
        return tollReady;
    }

    public boolean bindMatch(@Nullable UUID match) {
        if (Objects.equals(matchId, match)) {
            return false;
        }
        matchId = match;
        return true;
    }

    public boolean clear() {
        boolean changed = hasRoundState() || clientEchoRemainingTicks != 0 || clientHeardRemainingTicks != 0;
        matchId = null;
        echoTaskOrdinal = NO_TASK;
        echoDeadlineTick = 0L;
        heardUntilTick = 0L;
        tollReady = false;
        clientEchoRemainingTicks = 0;
        clientHeardRemainingTicks = 0;
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    /** Remaining Echo ticks: live on the server, last synced countdown on the client. / 回响剩余 tick：服务端实时计算，客户端为同步后的倒计时。 */
    public int echoRemainingTicks() {
        if (player.getWorld().isClient) {
            return clientEchoRemainingTicks;
        }
        return hasEchoTask() ? remainingUntil(echoDeadlineTick) : 0;
    }

    public int heardRemainingTicks() {
        if (player.getWorld().isClient) {
            return clientHeardRemainingTicks;
        }
        return remainingUntil(heardUntilTick);
    }

    public void syncOwner() {
        if (!player.getWorld().isClient) {
            KEY.sync(player);
        }
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
            BellRingerEchoRuntime.tick(serverPlayer, this);
            BellRingerLoadoutService.tick(serverPlayer);
        }
    }

    @Override
    public void clientTick() {
        if (clientEchoRemainingTicks > 0) {
            clientEchoRemainingTicks--;
        }
        if (clientHeardRemainingTicks > 0) {
            clientHeardRemainingTicks--;
        }
    }

    /**
     * Owner-only packet: task ordinal (+1, 0 = none), Echo remaining, hint remaining, toll flag.
     * Remaining values are computed at write time so the client never needs server world time.
     * 仅拥有者数据包：任务序号（+1，0 表示无）、回响剩余、提示剩余、敲钟标记；
     * 剩余值在写入时计算，客户端无需服务端世界时间。
     */
    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(hasEchoTask() ? echoTaskOrdinal + 1 : 0);
        buf.writeVarInt(echoRemainingTicks());
        buf.writeVarInt(heardRemainingTicks());
        buf.writeBoolean(tollReady);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        int encodedTask = buf.readVarInt();
        int echoRemaining = buf.readVarInt();
        int heardRemaining = buf.readVarInt();
        boolean ready = buf.readBoolean();
        int ordinal = encodedTask - 1;
        echoTaskOrdinal = ordinal >= 0 && ordinal < TASKS.length ? ordinal : NO_TASK;
        clientEchoRemainingTicks = echoTaskOrdinal == NO_TASK ? 0 : Math.max(0, echoRemaining);
        clientHeardRemainingTicks = Math.max(0, heardRemaining);
        tollReady = ready;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (matchId != null) {
            tag.putUuid("Match", matchId);
        }
        tag.putInt("EchoTask", echoTaskOrdinal);
        tag.putLong("EchoDeadline", echoDeadlineTick);
        tag.putLong("HeardUntil", heardUntilTick);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        int ordinal = tag.contains("EchoTask") ? tag.getInt("EchoTask") : NO_TASK;
        echoTaskOrdinal = ordinal >= 0 && ordinal < TASKS.length ? ordinal : NO_TASK;
        echoDeadlineTick = echoTaskOrdinal == NO_TASK ? 0L : Math.max(0L, tag.getLong("EchoDeadline"));
        heardUntilTick = Math.max(0L, tag.getLong("HeardUntil"));
        tollReady = false;
        clientEchoRemainingTicks = 0;
        clientHeardRemainingTicks = 0;
    }
}
