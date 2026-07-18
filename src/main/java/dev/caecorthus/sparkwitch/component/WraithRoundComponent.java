package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stores only the round-scoped Wraith conversion budget and consumed player slots.
 * 只保存本局冤魂转化上限与已消耗的玩家名额。
 */
public final class WraithRoundComponent implements Component {
    public static final ComponentKey<WraithRoundComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("wraith_round"),
            WraithRoundComponent.class
    );

    private static final String STARTING_PLAYER_COUNT_KEY = "StartingPlayerCount";
    private static final String CONSUMED_PLAYERS_KEY = "ConsumedPlayers";

    private final WraithRoundQuota quota = new WraithRoundQuota();

    public void beginRound(int startingPlayerCount) {
        quota.beginRound(startingPlayerCount);
    }

    public int getStartingPlayerCount() {
        return quota.getStartingPlayerCount();
    }

    public int getCap() {
        return quota.getCap();
    }

    public int getConsumedCount() {
        return quota.getConsumedCount();
    }

    public Set<UUID> getConsumedPlayers() {
        return quota.getConsumedPlayers();
    }

    public boolean hasCapacity() {
        return quota.hasCapacity();
    }

    public boolean tryConsume(UUID playerUuid) {
        return quota.tryConsume(playerUuid);
    }

    public void clearRound() {
        quota.clearRound();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt(STARTING_PLAYER_COUNT_KEY, quota.getStartingPlayerCount());
        NbtList consumed = new NbtList();
        quota.getConsumedPlayers().stream().map(UUID::toString).map(NbtString::of).forEach(consumed::add);
        tag.put(CONSUMED_PLAYERS_KEY, consumed);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        LinkedHashSet<UUID> restored = new LinkedHashSet<>();
        NbtList consumed = tag.getList(CONSUMED_PLAYERS_KEY, NbtElement.STRING_TYPE);
        for (int index = 0; index < consumed.size(); index++) {
            try {
                restored.add(UUID.fromString(consumed.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore one malformed entry without losing the rest of the current round snapshot.
                // 忽略单个损坏条目，同时保留本局其余有效快照。
            }
        }
        quota.restore(tag.getInt(STARTING_PLAYER_COUNT_KEY), restored);
    }
}
