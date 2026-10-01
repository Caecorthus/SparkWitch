package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Fixed full post-use cooldowns that pinned NoellesRoles 1.7.6 writes into the shared {@code AbilityPlayerComponent},
 * keyed by the writer's role id. NoellesRoles inlines them as {@code GameConstants.getInTicks(minutes, seconds)} in
 * private handlers, so they are mirrored here and pinned against the jar bytecode by a contract test. Round-start
 * cooldowns are not nominals. Absent on purpose: the Pathogen (dynamic, read from its component by the store) and the
 * Morphling (its morph only gates on this counter and never writes it).
 * 锁定的 NoellesRoles 1.7.6 在共享 AbilityPlayerComponent 中写入的固定使用后完整冷却，按写入者职业 id 索引。
 * NoellesRoles 在私有处理器中以 GameConstants.getInTicks(分, 秒) 内联这些值，因此在此镜像，并由契约测试对照 jar 字节码
 * 固定。开局冷却不算标准冷却。刻意缺省：病原体（动态值，由存储从其组件读取）与变形者（变形只用此计数做门控，从不写入）。
 */
final class NoellesAbilityNominals {
    private static final Map<Identifier, Integer> FIXED = build();

    private NoellesAbilityNominals() {
    }

    /** The fixed nominal of this role, or empty. / 该职业的固定标准冷却，或空。 */
    static OptionalInt fixedTicks(Identifier roleId) {
        Integer ticks = roleId == null ? null : FIXED.get(roleId);
        return ticks == null ? OptionalInt.empty() : OptionalInt.of(ticks);
    }

    static Map<Identifier, Integer> table() {
        return FIXED;
    }

    private static Map<Identifier, Integer> build() {
        Map<Identifier, Integer> entries = new LinkedHashMap<>();
        entries.put(role("voodoo"), ticks(0, 30));
        entries.put(role("vulture"), ticks(0, 5));
        entries.put(role("swapper"), ticks(1, 0));
        // The recall (teleport) value; placing the mark writes ticks(0, 10). / 召回（传送）的值；放置标记写入 ticks(0, 10)。
        entries.put(role("recaller"), ticks(0, 30));
        entries.put(role("phantom"), ticks(1, 30));
        entries.put(role("noisemaker"), ticks(3, 0));
        entries.put(role("reporter"), ticks(0, 30));
        entries.put(role("detective"), ticks(1, 30));
        entries.put(role("silencer"), ticks(0, 45));
        entries.put(role("party_animal"), ticks(1, 0));
        // Returning and a cancelled projection both write 1 min. / 主动返回与投射被取消都写入 1 分钟。
        entries.put(role("spiritualist"), ticks(1, 0));
        return Collections.unmodifiableMap(entries);
    }

    /** Mirrors Wathe {@code GameConstants.getInTicks}. / 镜像 Wathe 的 GameConstants.getInTicks。 */
    private static int ticks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }

    private static Identifier role(String path) {
        return Identifier.of(NoellesRoleIds.NAMESPACE, path);
    }
}
