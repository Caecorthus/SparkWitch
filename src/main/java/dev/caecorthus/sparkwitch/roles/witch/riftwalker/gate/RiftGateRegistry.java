package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerMatch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Frozen (G0) static facade over {@link RiftGateRegistryComponent}: the only code that adds or removes gate records,
 * spawns/discards their entities and holds their chunk tickets. Hops (P2), projectile exits (P4) and the console (P8)
 * read it; nothing else writes it. Server thread only. Owned by P1.
 * 冻结（G0）的静态门面，封装 {@link RiftGateRegistryComponent}：唯一负责增删门记录、生成/移除门实体并持有区块票的代码。
 * 跳门（P2）、投掷物出口（P4）与控制台（P8）只读它；其他代码不得写入。仅服务端线程。归属 P1。
 *
 * <p>Chunk tickets: every record holds one non-persistent ticket of {@link #TICKET} at level 31 (radius
 * {@link #TICKET_RADIUS}) on the chunk of its position, keyed by the gate number, so the gate's chunk always ticks
 * entities and an unsaved gate is never dropped by a chunk unload. Tickets are released whenever a record leaves
 * (close, round-edge clear, binding to another match). If an entity still goes missing, {@link #repair} spawns it again
 * from its record on the next world tick.
 * 区块票：每条记录在其位置所在区块上持有一张 {@link #TICKET} 非持久票，等级 31（半径 {@link #TICKET_RADIUS}），
 * 以门编号为键，保证门所在区块始终 tick 实体，不存盘的门不会因区块卸载而丢失。记录离开时（关闭、对局边界清理、绑定到
 * 其他对局）总会释放区块票。若实体仍然丢失，{@link #repair} 会在下一个世界 tick 按记录重新生成它。
 */
public final class RiftGateRegistry {
    /**
     * Non-persistent (never saved, never expires) ticket per gate; the argument is the gate number.
     * 每扇门一张非持久（不存盘、不过期）区块票；参数为门编号。
     */
    static final ChunkTicketType<Integer> TICKET = ChunkTicketType.create("sparkwitch_rift_gate", Integer::compare);
    /** {@code 33 - 2 = 31}: the ticketed chunk ticks entities. / {@code 33 - 2 = 31}：持票区块会 tick 实体。 */
    static final int TICKET_RADIUS = 2;

    private RiftGateRegistry() {
    }

    // ---- Reads (frozen) / 读取（冻结） ----

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

    /** The registry lists this very entity under its number (entity self-check). / 登记表以其编号列出了这一实体（实体自检）。 */
    public static boolean isListed(ServerWorld world, RiftGateEntity gate) {
        return RiftGateLifecycleRules.isListed(byNumber(world, gate.gateNumber()), gate.getUuid());
    }

    // ---- Writes / 写入 ----

    /**
     * Binds this world's registry to {@code matchId} (dropping records, entities and tickets of another match), then
     * hands out the next gate number. Always allocate through here so a match change can never renumber after the
     * allocation.
     * 先把本世界登记表绑定到 {@code matchId}（丢弃其他对局的记录、实体与区块票），再分配下一个门编号。务必经此分配，
     * 以免分配之后因换局而重新编号。
     */
    public static int allocateNumber(ServerWorld world, @Nullable String matchId) {
        RiftGateRegistryComponent registry = RiftGateRegistryComponent.KEY.get(world);
        bindMatch(world, registry, matchId);
        return registry.allocateNumber();
    }

    /**
     * Placement transaction core (P1): allocates a number, spawns an initialised gate at {@code pos} facing
     * {@code facing}, and registers it (record + ticket). Returns the spawned gate, or empty (nothing recorded) when
     * the world refused the entity.
     * 放置事务核心（P1）：分配编号，在 {@code pos} 处生成朝向 {@code facing} 且已初始化的门并登记（记录 + 区块票）。
     * 世界拒绝该实体时返回空（不留任何记录）。
     */
    public static Optional<RiftGateEntity> spawn(ServerWorld world, Vec3d pos, Direction facing, UUID placer,
                                                 String matchId) {
        int number = allocateNumber(world, matchId);
        RiftGateEntity gate = create(world, number, pos, facing, placer, matchId);
        if (gate == null || !world.spawnEntity(gate)) {
            return Optional.empty();
        }
        register(world, gate);
        return Optional.of(gate);
    }

    /**
     * Records a freshly spawned gate whose server state ({@link RiftGateEntity#initServerState}) is already set, and
     * returns its record. The caller (placement, P1) allocates the number first through
     * {@link #allocateNumber(ServerWorld, String)}; this binds the component to the gate's match (a no-op when already
     * bound) and adds the gate's chunk ticket.
     * 记录一扇刚生成、服务端状态（{@link RiftGateEntity#initServerState}）已设置的门并返回其记录。调用方（放置，P1）先经
     * {@link #allocateNumber(ServerWorld, String)} 分配编号；此处把组件绑定到门的对局（已绑定时为空操作）并添加该门的区块票。
     */
    public static RiftGateRecord register(ServerWorld world, RiftGateEntity gate) {
        RiftGateRegistryComponent registry = RiftGateRegistryComponent.KEY.get(world);
        bindMatch(world, registry, gate.matchId());
        RiftGateRecord record = new RiftGateRecord(gate.gateNumber(), gate.getUuid(), gate.getPos(), gate.facing(),
                gate.placer(), world.getTime());
        // A replaced record (same number) gives its ticket back first. / 被替换的同编号记录先归还其区块票。
        registry.byNumber(record.number()).ifPresent(previous -> removeTicket(world, previous));
        registry.add(record);
        addTicket(world, record);
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
        removeTicket(world, removed.get());
        RiftSessionService.onGateRemoved(world, removed.get(), reason);
        return true;
    }

    /**
     * Silent round-edge clear of this world: drops every record (and ticket) and discards every gate entity; never
     * notifies sessions (round-end session cleanup is P2's own, without cooldown).
     * 本世界的对局边界静默清理：丢弃所有记录（及区块票）并移除所有门实体；从不通知会话（对局结束的会话清理由 P2 自行完成，不上冷却）。
     */
    public static void clear(ServerWorld world) {
        RiftGateRegistryComponent registry = RiftGateRegistryComponent.KEY.get(world);
        releaseTickets(world, registry.gates());
        registry.clear();
        RiftGateEntity.discardAll(world);
    }

    /**
     * Server stop: forget the records only. Worlds are already closed, so no ticket or entity is touched (neither is
     * saved anyway).
     * 服务器停止：只遗忘记录。此时世界已关闭，因此不触碰区块票与实体（两者本就不存盘）。
     */
    static void forget(ServerWorld world) {
        RiftGateRegistryComponent.KEY.get(world).clear();
    }

    /**
     * Registry repair, once per world tick (cheap no-op without records): a registry still bound to an earlier match is
     * cleared silently; while the round runs, a record whose entity is gone is spawned again (same number, placer,
     * match, position and facing; only the entity id in the record changes) once its chunk ticks entities. If the world
     * refuses the new entity the gate is closed as {@link RiftGateCloseReason#LOST} so nobody stays inside a gate that
     * no longer exists.
     * 登记修复，每个世界 tick 一次（无记录时几乎零开销）：仍绑定到更早对局的登记表被静默清空；对局进行中，实体已丢失的记录
     * 会在其区块可 tick 实体后按记录重新生成（编号、放置者、对局、位置与朝向不变，只有记录中的实体 id 改变）。若世界拒绝
     * 新实体，则以 {@link RiftGateCloseReason#LOST} 关闭该门，确保没人留在已不存在的门里。
     */
    static void repair(ServerWorld world) {
        RiftGateRegistryComponent registry = RiftGateRegistryComponent.KEY.get(world);
        if (registry.gates().isEmpty()) {
            return;
        }
        String currentMatch = RiftwalkerMatch.currentMatchId(world);
        if (RiftGateLifecycleRules.holdsStaleRecords(true, registry.matchId(), currentMatch)) {
            clear(world);
            return;
        }
        boolean running = GameWorldComponent.KEY.get(world).isRunning();
        if (!RiftGateLifecycleRules.shouldRepair(running, registry.matchId(), currentMatch)) {
            return;
        }
        for (RiftGateRecord record : List.copyOf(registry.gates())) {
            Entity entity = world.getEntity(record.entityId());
            boolean present = entity instanceof RiftGateEntity gate && !gate.isRemoved();
            switch (RiftGateLifecycleRules.repairAction(present, world.shouldTickEntity(BlockPos.ofFloored(record.pos())))) {
                case KEEP -> {
                }
                case WAIT -> addTicket(world, record);
                case RESPAWN -> respawn(world, registry, record);
            }
        }
    }

    // ---- Internal / 内部 ----

    private static void respawn(ServerWorld world, RiftGateRegistryComponent registry, RiftGateRecord record) {
        String matchId = registry.matchId();
        RiftGateEntity gate = matchId == null ? null
                : create(world, record.number(), record.pos(), record.facing(), record.placer(), matchId);
        if (gate == null || !world.spawnEntity(gate)) {
            SparkWitch.LOGGER.warn("Rift Gate #{} in {} was lost and could not be restored; closing it",
                    record.number(), world.getRegistryKey().getValue());
            close(world, record.number(), RiftGateCloseReason.LOST);
            return;
        }
        SparkWitch.LOGGER.warn("Rift Gate #{} in {} was lost; restored it from the registry",
                record.number(), world.getRegistryKey().getValue());
        registry.add(new RiftGateRecord(record.number(), gate.getUuid(), record.pos(), record.facing(),
                record.placer(), record.createdTick()));
        addTicket(world, record);
    }

    @Nullable
    private static RiftGateEntity create(ServerWorld world, int number, Vec3d pos, Direction facing, UUID placer,
                                         String matchId) {
        RiftGateEntity gate = RiftGateEntities.riftGate().create(world);
        if (gate == null) {
            return null;
        }
        Direction front = RiftGatePlacementRules.horizontal(facing);
        gate.initServerState(number, placer, matchId);
        gate.setFacing(front);
        gate.refreshPositionAndAngles(pos.x, pos.y, pos.z, front.asRotation(), 0.0F);
        return gate;
    }

    /**
     * A different match drops the old records, their tickets and their entities before binding (silent, like the
     * round-edge clear); the same match is a no-op.
     * 换局时先丢弃旧记录、其区块票与实体再绑定（静默，与对局边界清理相同）；同一对局为空操作。
     */
    private static void bindMatch(ServerWorld world, RiftGateRegistryComponent registry, @Nullable String matchId) {
        if (Objects.equals(registry.matchId(), matchId)) {
            return;
        }
        List<RiftGateRecord> stale = List.copyOf(registry.gates());
        releaseTickets(world, stale);
        for (RiftGateRecord record : stale) {
            Entity entity = world.getEntity(record.entityId());
            if (entity instanceof RiftGateEntity gate && !gate.isRemoved()) {
                gate.discard();
            }
        }
        registry.bindMatch(matchId);
    }

    private static void releaseTickets(ServerWorld world, List<RiftGateRecord> records) {
        for (RiftGateRecord record : records) {
            removeTicket(world, record);
        }
    }

    private static void addTicket(ServerWorld world, RiftGateRecord record) {
        world.getChunkManager().addTicket(TICKET, chunkOf(record), TICKET_RADIUS, record.number());
    }

    private static void removeTicket(ServerWorld world, RiftGateRecord record) {
        world.getChunkManager().removeTicket(TICKET, chunkOf(record), TICKET_RADIUS, record.number());
    }

    private static ChunkPos chunkOf(RiftGateRecord record) {
        return new ChunkPos(BlockPos.ofFloored(record.pos()));
    }
}
