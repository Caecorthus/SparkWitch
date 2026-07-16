package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
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
 * Owner-private Perception window, incomplete points, and completed identity snapshots.
 * 仅拥有者可见的感知窗口、未完成点数与已解锁身份快照。
 */
public final class BlackRavenPerceptionPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<BlackRavenPerceptionPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("black_raven_perception"),
            BlackRavenPerceptionPlayerComponent.class
    );
    private static final int MAX_TARGETS = 256;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_ROLE_ID_LENGTH = 128;

    private final PlayerEntity player;
    private final BlackRavenPerceptionState state = new BlackRavenPerceptionState();
    private boolean ownsBlindness;

    public BlackRavenPerceptionPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int activeTicks() {
        return state.activeTicks();
    }

    public boolean isActive() {
        return state.isActive();
    }

    public boolean hasRoundState() {
        return state.matchId() != null || state.isActive()
                || !state.progress().isEmpty() || !state.completedSnapshots().isEmpty();
    }

    public @Nullable UUID matchId() {
        return state.matchId();
    }

    public List<BlackRavenIdentitySnapshot> completedSnapshots() {
        return state.completedSnapshots();
    }

    public @Nullable BlackRavenIdentitySnapshot snapshot(UUID targetUuid) {
        return state.snapshot(targetUuid);
    }

    public boolean bindMatch(@Nullable UUID matchId) {
        boolean changed = state.bindMatch(matchId);
        if (changed) {
            ownsBlindness = false;
            syncOwner();
        }
        return changed;
    }

    public boolean begin(int durationTicks, boolean ownsBlindness) {
        if (!state.begin(durationTicks)) {
            return false;
        }
        this.ownsBlindness = ownsBlindness;
        syncOwner();
        return true;
    }

    public int decrementActiveTicks() {
        return state.decrementActiveTicks();
    }

    public boolean accumulate(
            UUID targetUuid,
            int qualifyingTicks,
            Supplier<BlackRavenIdentitySnapshot> snapshotFactory
    ) {
        boolean revealed = state.accumulate(targetUuid, qualifyingTicks, snapshotFactory);
        if (revealed) {
            syncOwner();
        }
        return revealed;
    }

    public boolean ownsBlindness() {
        return ownsBlindness;
    }

    public void releaseBlindnessOwnership() {
        ownsBlindness = false;
    }

    public boolean cancelActivePreservingKnowledge() {
        boolean changed = state.cancelActive();
        ownsBlindness = false;
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    public void clear() {
        boolean changed = state.matchId() != null || state.isActive() || !state.completedSnapshots().isEmpty()
                || !state.progress().isEmpty() || ownsBlindness;
        state.clear();
        ownsBlindness = false;
        if (changed) {
            syncOwner();
        }
    }

    public void syncOwner() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            BlackRavenPerceptionService.tick(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        state.decrementActiveTicks();
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(state.activeTicks());
        List<BlackRavenIdentitySnapshot> snapshots = state.completedSnapshots();
        buf.writeVarInt(Math.min(MAX_TARGETS, snapshots.size()));
        for (int index = 0; index < snapshots.size() && index < MAX_TARGETS; index++) {
            writeSnapshot(buf, snapshots.get(index));
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        int activeTicks = Math.max(0, buf.readVarInt());
        int size = Math.clamp(buf.readVarInt(), 0, MAX_TARGETS);
        List<BlackRavenIdentitySnapshot> snapshots = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            snapshots.add(readSnapshot(buf));
        }
        state.restore(state.matchId(), activeTicks, Map.of(), snapshots);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (state.matchId() != null) {
            tag.putUuid("Match", state.matchId());
        }
        tag.putInt("ActiveTicks", state.activeTicks());
        NbtList progressList = new NbtList();
        state.progress().forEach((targetUuid, progress) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Target", targetUuid);
            entry.putInt("Points", progress.points());
            entry.putInt("FractionalTicks", progress.fractionalTicks());
            progressList.add(entry);
        });
        tag.put("Progress", progressList);

        NbtList snapshotList = new NbtList();
        state.completedSnapshots().forEach(snapshot -> snapshotList.add(toNbt(snapshot)));
        tag.put("Snapshots", snapshotList);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        UUID matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        Map<UUID, BlackRavenPerceptionState.ProgressView> progress = new LinkedHashMap<>();
        NbtList progressList = tag.getList("Progress", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < progressList.size() && index < MAX_TARGETS; index++) {
            NbtCompound entry = progressList.getCompound(index);
            if (entry.containsUuid("Target")) {
                progress.put(entry.getUuid("Target"), new BlackRavenPerceptionState.ProgressView(
                        entry.getInt("Points"),
                        entry.getInt("FractionalTicks")
                ));
            }
        }
        List<BlackRavenIdentitySnapshot> snapshots = new ArrayList<>();
        NbtList snapshotList = tag.getList("Snapshots", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < snapshotList.size() && index < MAX_TARGETS; index++) {
            BlackRavenIdentitySnapshot snapshot = fromNbt(snapshotList.getCompound(index));
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        state.restore(matchId, Math.max(0, tag.getInt("ActiveTicks")), progress, snapshots);
        ownsBlindness = false;
    }

    private static void writeSnapshot(RegistryByteBuf buf, BlackRavenIdentitySnapshot snapshot) {
        buf.writeUuid(snapshot.targetUuid());
        buf.writeString(snapshot.playerName(), MAX_NAME_LENGTH);
        buf.writeString(snapshot.roleId(), MAX_ROLE_ID_LENGTH);
        buf.writeInt(snapshot.roleColor());
    }

    private static BlackRavenIdentitySnapshot readSnapshot(RegistryByteBuf buf) {
        return new BlackRavenIdentitySnapshot(
                buf.readUuid(),
                buf.readString(MAX_NAME_LENGTH),
                buf.readString(MAX_ROLE_ID_LENGTH),
                buf.readInt()
        );
    }

    private static NbtCompound toNbt(BlackRavenIdentitySnapshot snapshot) {
        NbtCompound entry = new NbtCompound();
        entry.putUuid("Target", snapshot.targetUuid());
        entry.putString("PlayerName", snapshot.playerName());
        entry.putString("RoleId", snapshot.roleId());
        entry.putInt("RoleColor", snapshot.roleColor());
        return entry;
    }

    private static @Nullable BlackRavenIdentitySnapshot fromNbt(NbtCompound entry) {
        if (!entry.containsUuid("Target")) {
            return null;
        }
        return new BlackRavenIdentitySnapshot(
                entry.getUuid("Target"),
                entry.getString("PlayerName"),
                entry.getString("RoleId"),
                entry.getInt("RoleColor")
        );
    }
}
