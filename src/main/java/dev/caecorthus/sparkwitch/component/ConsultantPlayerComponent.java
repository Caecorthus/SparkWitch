package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stores the 顾问's recorded identities (names only, so the book still reads after a rename).
 * 保存顾问记录的名单（只存名字，改名后记录册仍可读）。
 */
public final class ConsultantPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<ConsultantPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("consultant_player"),
            ConsultantPlayerComponent.class
    );

    private final PlayerEntity player;
    private final Set<String> records = new LinkedHashSet<>();

    public ConsultantPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean record(String line) {
        dev.caecorthus.sparkwitch.SparkWitch.LOGGER.info("[consultant] record({}) for {}, now {} entries",
                line, player.getGameProfile().getName(), records.size() + 1);
        boolean changed = records.add(line);
        if (changed) {
            sync();
        }
        return changed;
    }

    public Set<String> records() {
        return Set.copyOf(records);
    }

    public void clearRecords() {
        if (!records.isEmpty()) {
            records.clear();
            sync();
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (String line : records) {
            list.add(NbtString.of(line));
        }
        tag.put("ConsultantRecords", list);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        records.clear();
        NbtList list = tag.getList("ConsultantRecords", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            records.add(list.getString(i));
        }
    }
}
