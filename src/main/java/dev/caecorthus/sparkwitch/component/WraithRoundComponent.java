package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    private final @Nullable Object provider;
    private final WraithRoundQuota quota = new WraithRoundQuota();
    private boolean canonicalDataPresent;
    private boolean legacyImportCompleted;

    public WraithRoundComponent(@Nullable Object provider) {
        this.provider = provider;
    }

    public void beginRound(int startingPlayerCount) {
        canonicalDataPresent = true;
        legacyImportCompleted = true;
        quota.beginRound(startingPlayerCount);
    }

    public int getStartingPlayerCount() {
        ensureLegacyImported();
        return quota.getStartingPlayerCount();
    }

    public int getCap() {
        ensureLegacyImported();
        return quota.getCap();
    }

    public int getConsumedCount() {
        ensureLegacyImported();
        return quota.getConsumedCount();
    }

    public Set<UUID> getConsumedPlayers() {
        ensureLegacyImported();
        return quota.getConsumedPlayers();
    }

    public boolean hasCapacity() {
        ensureLegacyImported();
        return quota.hasCapacity();
    }

    public boolean tryConsume(UUID playerUuid) {
        ensureLegacyImported();
        return quota.tryConsume(playerUuid);
    }

    public void clearRound() {
        quota.clearRound();
        LegacyWraithRoundComponent.KEY.maybeGet(provider).ifPresent(LegacyWraithRoundComponent::clear);
        canonicalDataPresent = false;
        legacyImportCompleted = false;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ensureLegacyImported();
        tag.putInt(STARTING_PLAYER_COUNT_KEY, quota.getStartingPlayerCount());
        NbtList consumed = new NbtList();
        for (UUID playerUuid : quota.getConsumedPlayers()) {
            consumed.add(NbtString.of(playerUuid.toString()));
        }
        tag.put(CONSUMED_PLAYERS_KEY, consumed);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        quota.restore(tag.getInt(STARTING_PLAYER_COUNT_KEY), readConsumedPlayers(tag));
        canonicalDataPresent = true;
        legacyImportCompleted = true;
    }

    /**
     * Canonical data wins even if CCA reads the legacy sibling first; import waits until first use.
     * 即使 CCA 先读旧组件，规范数据仍优先；旧数据只在首次使用时惰性导入。
     */
    private void ensureLegacyImported() {
        if (legacyImportCompleted || canonicalDataPresent) {
            legacyImportCompleted = true;
            return;
        }
        LegacyWraithRoundComponent.KEY.maybeGet(provider).ifPresent(legacy -> {
            if (legacy.hasData()) {
                quota.restore(legacy.getStartingPlayerCount(), legacy.getConsumedPlayers());
            }
        });
        legacyImportCompleted = true;
    }

    private static Set<UUID> readConsumedPlayers(NbtCompound tag) {
        LinkedHashSet<UUID> restoredConsumedPlayers = new LinkedHashSet<>();
        NbtList consumed = tag.getList(CONSUMED_PLAYERS_KEY, NbtElement.STRING_TYPE);
        for (int index = 0; index < consumed.size(); index++) {
            try {
                restoredConsumedPlayers.add(UUID.fromString(consumed.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore a malformed entry without losing the remaining round snapshot.
                // 忽略损坏条目，同时保留其余本局快照。
            }
        }
        return restoredConsumedPlayers;
    }
}
