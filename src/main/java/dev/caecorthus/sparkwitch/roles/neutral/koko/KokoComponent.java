package dev.caecorthus.sparkwitch.roles.neutral.koko;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stable CCA contract {@code sparkwitch:koko_player}: traffic, the live window and the shared cooldown, as absolute
 * world ticks on the server and remaining seconds on the client.
 * 稳定 CCA 契约 {@code sparkwitch:koko_player}：流量、直播窗口与共享冷却；服务端保存绝对刻，客户端保存剩余秒数。
 */
public final class KokoComponent implements AutoSyncedComponent {
    public static final ComponentKey<KokoComponent> KEY = ComponentRegistry.getOrCreate(
            KokoRules.ROLE_ID.withSuffixedPath("_player"), KokoComponent.class);
    private static final String TRAFFIC = "Traffic";
    private static final String STREAM_END = "StreamEnd";
    private static final String COOLDOWN_END = "CooldownEnd";

    private final PlayerEntity player;
    private int traffic;
    private long streamEndTick;
    private long cooldownEndTick;
    private int clientStreamSeconds;
    private int clientCooldownSeconds;

    public KokoComponent(PlayerEntity player) {
        this.player = player;
    }

    public int traffic() {
        return traffic;
    }

    public void addTraffic(int amount) {
        traffic = Math.max(0, traffic + amount);
        sync();
    }

    /** Server: absolute end tick. Client: remaining seconds from the last sync. 服务端：绝对结束刻；客户端：上次同步的剩余秒数。 */
    public int streamSeconds() {
        if (!player.getWorld().isClient) {
            long left = streamEndTick - player.getWorld().getTime();
            return (int) Math.max(0, left / 20L);
        }
        return clientStreamSeconds;
    }

    public boolean isStreaming() {
        return streamSeconds() > 0;
    }

    public int cooldownSeconds() {
        if (!player.getWorld().isClient) {
            long left = cooldownEndTick - player.getWorld().getTime();
            return (int) Math.max(0, left / 20L);
        }
        return clientCooldownSeconds;
    }

    public long streamEndTick() {
        return streamEndTick;
    }

    public long cooldownEndTick() {
        return cooldownEndTick;
    }

    public void setStreamEndTick(long tick) {
        streamEndTick = tick;
        sync();
    }

    public void setCooldownEndTick(long tick) {
        cooldownEndTick = tick;
        sync();
    }

    public void clear() {
        traffic = 0;
        streamEndTick = 0L;
        cooldownEndTick = 0L;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        traffic = tag.getInt(TRAFFIC);
        streamEndTick = tag.getLong(STREAM_END);
        cooldownEndTick = tag.getLong(COOLDOWN_END);
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt(TRAFFIC, traffic);
        tag.putLong(STREAM_END, streamEndTick);
        tag.putLong(COOLDOWN_END, cooldownEndTick);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(traffic);
        buf.writeInt(streamSeconds());
        buf.writeInt(cooldownSeconds());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        traffic = buf.readInt();
        clientStreamSeconds = buf.readInt();
        clientCooldownSeconds = buf.readInt();
    }
}
