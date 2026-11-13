package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Victim-owned delayed Feather Blade mark with recipient-private sync.
 * 由受害者持有的羽刃延迟标记，并按接收者私密同步。
 * <p>The packet carries one bit that is true only for the Raven who made the mark, so only that Raven ever needs to be
 * told when a mark is set or cleared; everyone else holds false before and after. A mark or clear therefore syncs to
 * the marker alone ({@link #syncMarker}): the same packet sent to the victim or to another tracking Raven carried
 * false, but its arrival told them a silent mark had just landed on this player. {@link #shouldSyncWith} still
 * serves CCA's own tracking-start sync, which is sent whatever the mark state is and so reveals nothing.
 * <p>数据包只携带一个比特，且只对做出标记的那名黑羽鸦为 true，因此标记被设置或清除时只需要通知这名黑羽鸦；其他人前后都是
 * false。所以设置与清除只同步给标记者（{@link #syncMarker}）：发给受害者或其他正在追踪的黑羽鸦的同一个数据包虽然携带 false，
 * 但它的到达本身就说明该玩家刚刚被无声标记。{@link #shouldSyncWith} 仍用于 CCA 自身在开始追踪时的同步，该同步与标记状态无关，
 * 因此不泄露任何信息。
 */
public final class BlackRavenMarkPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<BlackRavenMarkPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("black_raven_mark"),
            BlackRavenMarkPlayerComponent.class
    );

    private final PlayerEntity player;
    private @Nullable UUID markerUuid;
    private @Nullable UUID matchUuid;
    private long expiryTick;
    private boolean markedForLocalRaven;

    public BlackRavenMarkPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean hasMark() {
        return markerUuid != null;
    }

    public boolean isMarkedForLocalRaven() {
        return markedForLocalRaven;
    }

    public @Nullable UUID markerUuid() {
        return markerUuid;
    }

    public @Nullable UUID matchUuid() {
        return matchUuid;
    }

    public long expiryTick() {
        return expiryTick;
    }

    public boolean mark(UUID markerUuid, long expiryTick, UUID matchUuid) {
        if (hasMark() || markerUuid == null || matchUuid == null) {
            return false;
        }
        this.markerUuid = markerUuid;
        this.matchUuid = matchUuid;
        this.expiryTick = Math.max(0L, expiryTick);
        syncMarker(markerUuid);
        return true;
    }

    public boolean clear() {
        if (!hasMark() && matchUuid == null && expiryTick == 0L && !markedForLocalRaven) {
            return false;
        }
        // The former marker is the only client that holds true and must be told it is gone. / 只有原标记者的客户端持有 true，需要被告知已清除。
        UUID formerMarker = markerUuid;
        markerUuid = null;
        matchUuid = null;
        expiryTick = 0L;
        markedForLocalRaven = false;
        if (formerMarker != null) {
            syncMarker(formerMarker);
        }
        return true;
    }

    /**
     * Sends the current state to {@code marker} alone, and only if that player is among CCA's usual recipients (tracking
     * this player, or this player itself), exactly as the broadcast reached it before.
     * 只把当前状态发给 {@code marker}，且仅当该玩家属于 CCA 通常的接收者（正在追踪此玩家，或就是此玩家本人）时才发送，
     * 与此前广播到达它的条件完全一致。
     */
    private void syncMarker(UUID marker) {
        KEY.sync(player, this, recipient -> marker.equals(recipient.getUuid()));
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        if (recipient == player || markerUuid != null && markerUuid.equals(recipient.getUuid())) {
            return true;
        }
        return BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(recipient.getServerWorld()).getRole(recipient));
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            BlackRavenMarkRuntime.tick(serverPlayer, this);
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(markerUuid != null && markerUuid.equals(recipient.getUuid()));
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        markedForLocalRaven = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (markerUuid != null) {
            tag.putUuid("Marker", markerUuid);
        }
        if (matchUuid != null) {
            tag.putUuid("Match", matchUuid);
        }
        tag.putLong("ExpiryTick", expiryTick);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        markerUuid = tag.containsUuid("Marker") ? tag.getUuid("Marker") : null;
        matchUuid = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        expiryTick = Math.max(0L, tag.getLong("ExpiryTick"));
        markedForLocalRaven = false;
    }
}
