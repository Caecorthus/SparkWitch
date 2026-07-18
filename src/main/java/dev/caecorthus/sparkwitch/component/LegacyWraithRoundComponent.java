package dev.caecorthus.sparkwitch.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only holder for the former SparkTraits round component.
 * 仅只读承接旧 SparkTraits 本局组件，绝不回写旧命名空间。
 */
public final class LegacyWraithRoundComponent implements Component {
    public static final ComponentKey<LegacyWraithRoundComponent> KEY = ComponentRegistry.getOrCreate(
            Identifier.of("sparktraits", "wraith_round"),
            LegacyWraithRoundComponent.class
    );

    private int startingPlayerCount;
    private Set<UUID> consumedPlayers = Set.of();
    private boolean dataPresent;

    public LegacyWraithRoundComponent(Object provider) {
    }

    boolean hasData() {
        return dataPresent;
    }

    int getStartingPlayerCount() {
        return startingPlayerCount;
    }

    Set<UUID> getConsumedPlayers() {
        return consumedPlayers;
    }

    void clear() {
        startingPlayerCount = 0;
        consumedPlayers = Set.of();
        dataPresent = false;
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        LinkedHashSet<UUID> restoredConsumedPlayers = new LinkedHashSet<>();
        NbtList consumed = tag.getList("ConsumedPlayers", NbtElement.STRING_TYPE);
        for (int index = 0; index < consumed.size(); index++) {
            try {
                restoredConsumedPlayers.add(UUID.fromString(consumed.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore a malformed legacy entry without losing the remaining round snapshot.
                // 忽略损坏的旧条目，同时保留其余本局快照。
            }
        }
        startingPlayerCount = Math.max(0, tag.getInt("StartingPlayerCount"));
        consumedPlayers = Set.copyOf(restoredConsumedPlayers);
        dataPresent = true;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Legacy ids are read-only migration inputs; canonical components are the sole writers.
        // 旧 id 仅用于迁移读取；只有规范组件可以写入。
    }
}
