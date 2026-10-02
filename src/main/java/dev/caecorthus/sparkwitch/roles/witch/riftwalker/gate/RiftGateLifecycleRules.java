package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerMatch;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure decisions behind {@link RiftGateLifecycle} and the registry repair in {@link RiftGateRegistry}: the entity's
 * per-tick self-check and what to do with a record whose entity is missing. Kept free of Minecraft and Fabric state so
 * it is unit-testable.
 * {@link RiftGateLifecycle} 与 {@link RiftGateRegistry} 登记修复背后的纯判定：实体每 tick 的自检，以及记录存在但实体
 * 缺失时的处理方式。不依赖 Minecraft 与 Fabric 状态，便于单元测试。
 */
public final class RiftGateLifecycleRules {
    /** Per-world repair verdict for one record. / 单条记录的每世界修复结论。 */
    public enum RepairAction {
        /** The entity is loaded and alive. / 实体已加载且存活。 */
        KEEP,
        /** The entity is gone while its chunk ticks entities: spawn it again from the record. / 实体丢失且区块可 tick 实体：按记录重新生成。 */
        RESPAWN,
        /** The entity is missing but its chunk is not entity-ticking yet; the ticket will load it. / 区块尚未进入实体 tick，等待区块票加载。 */
        WAIT
    }

    private RiftGateLifecycleRules() {
    }

    /**
     * Server self-check of a gate entity: the round is running (ACTIVE or STOPPING), the gate's match binding equals the
     * current match (a null binding never matches), and the registry still lists this very entity.
     * 门实体的服务端自检：对局进行中（ACTIVE 或 STOPPING）、门的对局绑定与当前对局一致（null 绑定永不匹配），
     * 且登记表仍列出这一实体本身。
     */
    public static boolean passesSelfCheck(boolean running, @Nullable String boundMatch, @Nullable String currentMatch,
                                          boolean listed) {
        return running && RiftwalkerMatch.matches(boundMatch, currentMatch) && listed;
    }

    /** The record with the gate's number exists and points at this entity. / 该编号的记录存在且指向本实体。 */
    public static boolean isListed(Optional<RiftGateRecord> record, @Nullable UUID entityId) {
        return entityId != null && record.isPresent() && entityId.equals(record.get().entityId());
    }

    /**
     * Repair runs only while the round is running and the registry is bound to the current match; otherwise the
     * entities discard themselves and the round-edge sweep drops the records.
     * 只有在对局进行中且登记表绑定到当前对局时才修复；否则实体会自行移除，记录由对局边界清扫清空。
     */
    public static boolean shouldRepair(boolean running, @Nullable String registryMatch, @Nullable String currentMatch) {
        return running && RiftwalkerMatch.matches(registryMatch, currentMatch);
    }

    /** True when a registry bound to another match holds stale records. / 登记表绑定到其他对局且仍有过期记录时为 true。 */
    public static boolean holdsStaleRecords(boolean hasRecords, @Nullable String registryMatch,
                                            @Nullable String currentMatch) {
        return hasRecords && currentMatch != null && !RiftwalkerMatch.matches(registryMatch, currentMatch);
    }

    public static RepairAction repairAction(boolean entityPresent, boolean chunkTicksEntities) {
        if (entityPresent) {
            return RepairAction.KEEP;
        }
        return chunkTicksEntities ? RepairAction.RESPAWN : RepairAction.WAIT;
    }
}
