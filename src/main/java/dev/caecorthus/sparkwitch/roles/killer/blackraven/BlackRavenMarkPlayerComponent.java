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
        sync();
        return true;
    }

    public boolean clear() {
        if (!hasMark() && matchUuid == null && expiryTick == 0L && !markedForLocalRaven) {
            return false;
        }
        markerUuid = null;
        matchUuid = null;
        expiryTick = 0L;
        markedForLocalRaven = false;
        sync();
        return true;
    }

    private void sync() {
        KEY.sync(player);
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
