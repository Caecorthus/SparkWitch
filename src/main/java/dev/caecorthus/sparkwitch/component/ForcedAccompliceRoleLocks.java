package dev.caecorthus.sparkwitch.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player locks of the role a Bewitched is promoted to ({@code /sparkwitch:forceAccompliceRole}, D4/C5): the plain
 * Accomplice or one special accomplice per UUID. Server-only, never synced. NBT: a list of {@code Player}/{@code Role}
 * string pairs under {@code ForcedAccompliceRoles}; malformed entries are skipped.
 * 魔化使晋升身份的逐玩家锁定（{@code /sparkwitch:forceAccompliceRole}，D4/C5）：每个 UUID 对应普通共犯或一种特殊共犯。
 * 仅服务端、从不同步。NBT：{@code ForcedAccompliceRoles} 下的 {@code Player}/{@code Role} 字符串对列表；损坏条目会被跳过。
 */
final class ForcedAccompliceRoleLocks {
    static final String NBT_KEY = "ForcedAccompliceRoles";

    private final LinkedHashMap<UUID, Identifier> locks = new LinkedHashMap<>();

    /** Returns whether the stored lock changed. / 返回锁定是否发生变化。 */
    boolean set(UUID playerUuid, Identifier roleId) {
        return !roleId.equals(locks.put(playerUuid, roleId));
    }

    @Nullable
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
        for (Map.Entry<UUID, Identifier> lock : locks.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Player", lock.getKey().toString());
            entry.putString("Role", lock.getValue().toString());
            list.add(entry);
        }
        return list;
    }

    void readFromNbt(NbtCompound tag) {
        locks.clear();
        NbtList list = tag.getList(NBT_KEY, NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < list.size(); index++) {
            NbtCompound entry = list.getCompound(index);
            Identifier roleId = Identifier.tryParse(entry.getString("Role"));
            try {
                UUID playerUuid = UUID.fromString(entry.getString("Player"));
                if (roleId != null) {
                    locks.put(playerUuid, roleId);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry and keep the valid ones. / 跳过损坏条目，保留其他有效锁定。
            }
        }
    }
}
