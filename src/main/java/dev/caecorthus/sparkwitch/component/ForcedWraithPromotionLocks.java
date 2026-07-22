package dev.caecorthus.sparkwitch.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ForcedWraithPromotionLocks {
    private final LinkedHashMap<UUID, Identifier> locks = new LinkedHashMap<>();

    boolean set(UUID playerUuid, Identifier roleId) {
        return !roleId.equals(locks.put(playerUuid, roleId));
    }

    Identifier get(UUID playerUuid) {
        return locks.get(playerUuid);
    }

    boolean clear(UUID playerUuid) {
        return locks.remove(playerUuid) != null;
    }

    void clearAll() {
        locks.clear();
    }

    Map<UUID, Identifier> snapshot() {
        return Map.copyOf(locks);
    }

    NbtList toNbt() {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Identifier> forced : locks.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Player", forced.getKey().toString());
            entry.putString("Role", forced.getValue().toString());
            list.add(entry);
        }
        return list;
    }

    void readFromNbt(NbtList list) {
        locks.clear();
        for (int index = 0; index < list.size(); index++) {
            NbtCompound entry = list.getCompound(index);
            Identifier roleId = Identifier.tryParse(entry.getString("Role"));
            try {
                UUID playerUuid = UUID.fromString(entry.getString("Player"));
                if (roleId != null) {
                    locks.put(playerUuid, roleId);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries without discarding other valid promotion locks.
            }
        }
    }

    void readFromNbt(NbtCompound tag, String key) {
        readFromNbt(tag.getList(key, NbtElement.COMPOUND_TYPE));
    }
}
