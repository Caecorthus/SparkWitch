package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stores each Perfumer's private scent targets and each player's cologne timer.
 * 保存每名调香师私有的气味目标，以及每名玩家身上的古龙水计时。
 */
public final class PerfumerPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<PerfumerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("perfumer_player"),
            PerfumerPlayerComponent.class
    );

    private final PlayerEntity player;
    private final PerfumerState state = new PerfumerState();

    public PerfumerPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean mark(UUID targetUuid) {
        boolean changed = state.mark(targetUuid);
        if (changed) {
            sync();
        }
        return changed;
    }

    public boolean promoteMarked(UUID targetUuid) {
        boolean changed = state.promoteToBloody(targetUuid);
        if (changed) {
            sync();
        }
        return changed;
    }

    public boolean removeTarget(UUID targetUuid) {
        boolean changed = state.removeTarget(targetUuid);
        if (changed) {
            sync();
        }
        return changed;
    }

    public boolean isMarked(UUID targetUuid) {
        return state.isMarked(targetUuid);
    }

    public boolean isBloody(UUID targetUuid) {
        return state.isBloody(targetUuid);
    }

    public void startCologne() {
        state.startCologne();
        sync();
    }

    public boolean tickColognePulse() {
        int previousTicks = state.cologneTicks();
        boolean pulse = state.tickCologne();
        if (pulse || previousTicks > 0 && state.cologneTicks() == 0) {
            sync();
        }
        return pulse;
    }

    public void stopCologne() {
        if (state.stopCologne()) {
            sync();
        }
    }

    public void clear() {
        if (state.markedTargets().isEmpty()
                && state.bloodyTargets().isEmpty()
                && state.cologneTicks() == 0) {
            return;
        }
        state.clear();
        sync();
    }

    private void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Scent targets are owner-private; even observers must not receive another Perfumer's list.
        // 气味目标属于调香师私密信息；即使观察者也不能收到其他调香师的列表。
        return recipient == this.player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PerfumerRuntime.tickPlayer(serverPlayer, this);
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        writeUuids(buf, state.markedTargets());
        writeUuids(buf, state.bloodyTargets());
        buf.writeVarInt(state.cologneTicks());
        buf.writeVarInt(state.colognePulseTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        Set<UUID> marked = readUuids(buf);
        Set<UUID> bloody = readUuids(buf);
        state.restore(marked, bloody, buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.put("MarkedTargets", toNbt(state.markedTargets()));
        tag.put("BloodyTargets", toNbt(state.bloodyTargets()));
        tag.putInt("CologneTicks", state.cologneTicks());
        tag.putInt("ColognePulseTicks", state.colognePulseTicks());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        Set<UUID> marked = fromNbt(tag.getList("MarkedTargets", NbtElement.STRING_TYPE));
        Set<UUID> bloody = fromNbt(tag.getList("BloodyTargets", NbtElement.STRING_TYPE));
        state.restore(marked, bloody, tag.getInt("CologneTicks"), tag.getInt("ColognePulseTicks"));
    }

    private static void writeUuids(RegistryByteBuf buf, Set<UUID> uuids) {
        buf.writeVarInt(uuids.size());
        uuids.forEach(buf::writeUuid);
    }

    private static Set<UUID> readUuids(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        Set<UUID> uuids = new LinkedHashSet<>();
        for (int index = 0; index < size; index++) {
            uuids.add(buf.readUuid());
        }
        return uuids;
    }

    private static NbtList toNbt(Set<UUID> uuids) {
        NbtList list = new NbtList();
        uuids.stream().map(UUID::toString).map(NbtString::of).forEach(list::add);
        return list;
    }

    private static Set<UUID> fromNbt(NbtList list) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            try {
                uuids.add(UUID.fromString(list.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy data rather than blocking a world load.
                // 忽略损坏的旧存档字段，避免阻止世界加载。
            }
        }
        return uuids;
    }
}
