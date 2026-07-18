package dev.caecorthus.sparkwitch.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Exact read-boundary aliases for the six former SparkTraits role ids. / 六个旧 SparkTraits 角色 id 的精确读取别名。 */
public final class WraithLegacyRoleIds {
    private static final Map<Identifier, Identifier> LEGACY_TO_CANONICAL = Map.ofEntries(
            alias("wraith"),
            alias("wind_spirit"),
            alias("guardian_angel"),
            alias("vendetta"),
            alias("saboteur"),
            alias("curser")
    );
    private static final Map<String, String> LEGACY_STRINGS = Map.ofEntries(
            stringAlias("wraith"),
            stringAlias("wind_spirit"),
            stringAlias("guardian_angel"),
            stringAlias("vendetta"),
            stringAlias("saboteur"),
            stringAlias("curser")
    );

    private WraithLegacyRoleIds() {
    }

    public static Identifier canonicalize(Identifier roleId) {
        return LEGACY_TO_CANONICAL.getOrDefault(roleId, roleId);
    }

    /** Copies exact old role lists only when absent and canonicalizes exact disabled-role strings. / 仅补齐缺失的精确旧角色列表，并规范化精确禁用角色字符串。 */
    public static void migrateGameWorldNbt(NbtCompound nbt) {
        for (Map.Entry<Identifier, Identifier> alias : LEGACY_TO_CANONICAL.entrySet()) {
            String legacy = alias.getKey().toString();
            String canonical = alias.getValue().toString();
            if (!nbt.contains(canonical) && nbt.contains(legacy)) {
                NbtElement legacyValue = nbt.get(legacy);
                if (legacyValue != null) {
                    nbt.put(canonical, legacyValue.copy());
                }
            }
        }
        canonicalizeStringList(nbt, "DisabledRoles");
    }

    /** Canonicalizes only exact saved RoleId values inside Wathe history. / 仅规范化 Wathe 历史中的精确 RoleId 值。 */
    public static void migrateRoleHistoryNbt(NbtCompound nbt) {
        NbtList players = nbt.getList("History", NbtElement.COMPOUND_TYPE);
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            NbtList entries = players.getCompound(playerIndex).getList("Entries", NbtElement.COMPOUND_TYPE);
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                NbtCompound entry = entries.getCompound(entryIndex);
                String roleId = entry.getString("RoleId");
                String canonical = LEGACY_STRINGS.get(roleId);
                if (canonical != null) {
                    entry.putString("RoleId", canonical);
                }
            }
        }
    }

    private static void canonicalizeStringList(NbtCompound nbt, String key) {
        if (!nbt.contains(key, NbtElement.LIST_TYPE)) {
            return;
        }
        NbtList original = nbt.getList(key, NbtElement.STRING_TYPE);
        NbtList canonicalized = new NbtList();
        for (int index = 0; index < original.size(); index++) {
            String stored = original.getString(index);
            canonicalized.add(NbtString.of(LEGACY_STRINGS.getOrDefault(stored, stored)));
        }
        nbt.put(key, canonicalized);
    }

    private static Map.Entry<Identifier, Identifier> alias(String path) {
        return Map.entry(Identifier.of("sparktraits", path), Identifier.of("sparkwitch", path));
    }

    private static Map.Entry<String, String> stringAlias(String path) {
        return Map.entry("sparktraits:" + path, "sparkwitch:" + path);
    }
}
