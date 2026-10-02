package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Optional;

/**
 * Frozen (G0) static facade over {@link RiftGateRegistryComponent}: the only code that adds or removes gate records,
 * spawns/discards their entities and holds their chunk tickets. Hops (P2), projectile exits (P4) and the console (P8)
 * read it; nothing else writes it. Server thread only. Owned by P1.
 * 冻结（G0）的静态门面，封装 {@link RiftGateRegistryComponent}：唯一负责增删门记录、生成/移除门实体并持有区块票的代码。
 * 跳门（P2）、投掷物出口（P4）与控制台（P8）只读它；其他代码不得写入。仅服务端线程。归属 P1。
 */
public final class RiftGateRegistry {
    private RiftGateRegistry() {
    }

    /** Live records of this world ordered by number. / 本世界按编号排序的存活记录。 */
    public static List<RiftGateRecord> gates(ServerWorld world) {
        return List.copyOf(RiftGateRegistryComponent.KEY.get(world).gates());
    }

    public static Optional<RiftGateRecord> byNumber(ServerWorld world, int number) {
        return RiftGateRegistryComponent.KEY.get(world).byNumber(number);
    }

    /** The gate entity if its record exists and the entity is currently loaded. / 记录存在且实体已加载时返回门实体。 */
    public static Optional<RiftGateEntity> entity(ServerWorld world, int number) {
        return byNumber(world, number)
                .map(record -> world.getEntity(record.entityId()))
                .filter(RiftGateEntity.class::isInstance)
                .map(RiftGateEntity.class::cast)
                .filter(gate -> !gate.isRemoved());
    }

    /**
     * Records a freshly spawned gate whose server state ({@link RiftGateEntity#initServerState}) is already set, and
     * returns its record. The caller (placement, P1) allocates the number first through the component.
     * 记录一扇刚生成、服务端状态（{@link RiftGateEntity#initServerState}）已设置的门并返回其记录。
     * 调用方（放置，P1）先通过组件分配编号。
     */
    public static RiftGateRecord register(ServerWorld world, RiftGateEntity gate) {
        // TODO(P1): bind the component to the current match id and add the per-gate chunk ticket (level 31).
        // TODO(P1)：把组件绑定到当前对局 id，并为每扇门添加区块票（等级 31）。
        RiftGateRecord record = new RiftGateRecord(gate.gateNumber(), gate.getUuid(), gate.getPos(), gate.facing(),
                gate.placer(), world.getTime());
        RiftGateRegistryComponent.KEY.get(world).add(record);
        return record;
    }

    /**
     * Removes the record, discards the entity if loaded, releases its chunk ticket, then tells the session service so
     * occupants are force-exited at the removed gate. Returns false when no such gate exists.
     * 移除记录、移除已加载的实体、释放区块票，然后通知会话服务，使门内的人在被移除的门处强制出门。门不存在时返回 false。
     */
    public static boolean close(ServerWorld world, int number, RiftGateCloseReason reason) {
        Optional<RiftGateRecord> removed = RiftGateRegistryComponent.KEY.get(world).remove(number);
        if (removed.isEmpty()) {
            return false;
        }
        Entity entity = world.getEntity(removed.get().entityId());
        if (entity instanceof RiftGateEntity gate && !gate.isRemoved()) {
            gate.discard();
        }
        // TODO(P1): remove the chunk ticket for removed.get().pos(). / TODO(P1)：移除该门的区块票。
        RiftSessionService.onGateRemoved(world, removed.get(), reason);
        return true;
    }

    /**
     * Silent round-edge clear of this world: drops every record (and ticket) and discards every gate entity; never
     * notifies sessions (round-end session cleanup is P2's own, without cooldown).
     * 本世界的对局边界静默清理：丢弃所有记录（及区块票）并移除所有门实体；从不通知会话（对局结束的会话清理由 P2 自行完成，不上冷却）。
     */
    public static void clear(ServerWorld world) {
        // TODO(P1): release every chunk ticket held for this world's gates. / TODO(P1)：释放本世界所有门的区块票。
        RiftGateRegistryComponent.KEY.get(world).clear();
        RiftGateEntity.discardAll(world);
    }
}
