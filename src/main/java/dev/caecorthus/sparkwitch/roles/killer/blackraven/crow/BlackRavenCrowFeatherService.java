package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowDropFeatherC2SPayload;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server side of the Homing Feather (归羽). {@link #handleDrop} receives {@code sparkwitch:black_raven_crow_drop_feather}:
 * at most once per session it spawns a {@link BlackRavenFeatherEntity} at the crow and records it with
 * {@link BlackRavenCrowPlayerComponent#setFeather}. While the session runs an expiring chunk ticket keeps the marker's
 * chunk entity-ticking (so it lands even far behind the crow) and the marker reports its rest position. When the session
 * ends, for any reason, {@link #takeFeather} removes the marker and clears the component before the landing is decided;
 * a marker whose session no longer names it removes itself, and the round end sweeps every marker.
 * 归羽的服务端部分。{@link #handleDrop} 接收 {@code black_raven_crow_drop_feather}：每次会话至多一次，在乌鸦位置生成
 * {@link BlackRavenFeatherEntity} 并以 {@link BlackRavenCrowPlayerComponent#setFeather} 记录。会话期间，一张会过期的区块票据保持标记所在区块
 * 处理实体（因此即使远离乌鸦也能落地），标记会上报其静止位置。会话以任何方式结束时，{@link #takeFeather} 在决定落地之前移除标记并清空组件；
 * 会话不再指向的标记会自行移除，回合结束时清扫所有标记。
 */
public final class BlackRavenCrowFeatherService {
    /** Around a dropped feather; argument: the marker entity id. / 位于已丢出的归羽周围；参数为标记实体 id。 */
    private static final ChunkTicketType<Integer> FEATHER_TICKET = ChunkTicketType.create(
            SparkWitch.MOD_ID + ":homing_feather", Integer::compare, BlackRavenCrowFeatherRules.TICKET_EXPIRY_TICKS);

    private BlackRavenCrowFeatherService() {
    }

    /** Silent on every refusal (no session, a feather already out). / 任何拒绝都静默（无会话、已丢出过）。 */
    public static void handleDrop(@Nullable ServerPlayerEntity player,
                                  @Nullable BlackRavenCrowDropFeatherC2SPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.get(player);
        if (crow == null || !BlackRavenCrowFeatherRules.canDrop(component.isActive(), component.hasFeather())) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        BlackRavenFeatherEntity feather = BlackRavenFeatherRegistry.featherMarker().create(world);
        if (feather == null) {
            return;
        }
        feather.setOwner(player.getUuid());
        feather.refreshPositionAndAngles(crow.getX(), crow.getY(), crow.getZ(), crow.getYaw(), 0.0F);
        feather.setVelocity(Vec3d.ZERO);
        if (!world.spawnEntity(feather)) {
            return;
        }
        feather.setReportedPos(feather.getPos());
        component.setFeather(feather.getPos(), feather.getId());
        addTicket(world, feather.getPos(), feather.getId());
    }

    /**
     * Server tick of a marker (from {@link BlackRavenFeatherEntity#tick}): removes it unless its owner's live session
     * still names it, otherwise reports a moved position to the owner's component.
     * 标记的服务端刻：除非主人的有效会话仍指向它，否则将其移除；否则把变化后的位置同步到主人的组件。
     */
    static void tickMarker(BlackRavenFeatherEntity feather) {
        if (!(feather.getWorld() instanceof ServerWorld world)) {
            return;
        }
        UUID ownerId = feather.ownerUuid();
        ServerPlayerEntity owner = ownerId == null ? null : world.getServer().getPlayerManager().getPlayer(ownerId);
        BlackRavenCrowPlayerComponent component = owner == null
                ? null : BlackRavenCrowPlayerComponent.KEY.getNullable(owner);
        boolean keep = BlackRavenCrowFeatherRules.keepsMarker(
                owner != null && owner.getServerWorld() == world,
                component != null && component.isActive(),
                component == null ? -1 : component.getFeatherEntityId(),
                feather.getId());
        if (!keep) {
            feather.discard();
            return;
        }
        if (BlackRavenCrowFeatherRules.reportsMove(feather.reportedPos(), feather.getPos())) {
            feather.setReportedPos(feather.getPos());
            component.updateFeatherPos(feather.getPos());
        }
    }

    /** END_WORLD_TICK: refresh the ticket of every live session's feather. / 刷新每个有效会话的归羽区块票据。 */
    static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
            if (component != null && component.isActive() && component.hasFeather()
                    && component.getFeatherEntityId() >= 0) {
                addTicket(world, component.getFeatherPos(), component.getFeatherEntityId());
            }
        }
    }

    /**
     * Session end hook (before the landing is decided): removes the session's marker and clears the feather fields.
     * Returns where the feather rests, projected straight down to the floor if it was still falling, or null when no
     * feather was dropped. / 会话结束钩子（在决定落地之前）：移除会话的标记并清空归羽字段。返回归羽的静止位置（若仍在下落则向正下方投射到
     * 地面）；未丢出归羽时返回 null。
     */
    static @Nullable Vec3d takeFeather(ServerPlayerEntity owner, BlackRavenCrowPlayerComponent component) {
        if (!component.hasFeather()) {
            if (component.getFeatherEntityId() >= 0) {
                component.clearFeather();
            }
            return null;
        }
        ServerWorld world = owner.getServerWorld();
        Vec3d rest = component.getFeatherPos();
        boolean grounded = false;
        Entity entity = component.getFeatherEntityId() >= 0 ? world.getEntityById(component.getFeatherEntityId()) : null;
        if (entity instanceof BlackRavenFeatherEntity feather && !feather.isRemoved()) {
            rest = feather.getPos();
            grounded = feather.isOnGround();
            feather.discard();
        }
        component.clearFeather();
        return grounded ? rest : projectToFloor(world, owner, rest);
    }

    /** Round-end sweep of every marker in the world. / 回合结束时清扫该世界中的所有标记。 */
    static void discardAll(ServerWorld world) {
        for (BlackRavenFeatherEntity feather : world.getEntitiesByType(
                TypeFilter.instanceOf(BlackRavenFeatherEntity.class), EntityPredicates.VALID_ENTITY)) {
            feather.discard();
        }
    }

    private static Vec3d projectToFloor(ServerWorld world, ServerPlayerEntity owner, Vec3d from) {
        if (!BlackRavenCrowUseRules.isFinite(from)) {
            return from;
        }
        BlockHitResult floor = world.raycast(new RaycastContext(from,
                from.add(0.0, -BlackRavenCrowFeatherRules.MAX_PROJECTED_DROP, 0.0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.of(owner)));
        return floor.getType() == HitResult.Type.BLOCK ? new Vec3d(from.x, floor.getPos().y, from.z) : from;
    }

    private static void addTicket(ServerWorld world, Vec3d pos, int featherId) {
        if (!BlackRavenCrowUseRules.isFinite(pos)) {
            return;
        }
        world.getChunkManager().addTicket(FEATHER_TICKET, new ChunkPos(BlockPos.ofFloored(pos)),
                BlackRavenCrowFeatherRules.TICKET_RADIUS, featherId);
    }
}
