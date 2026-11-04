package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.compat.SparkTraitsControlExpertBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHitRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.RevolverItem;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The one server-side target pick of the forced shot, shared by {@link DominatorForcedShotService#fireNow} and the
 * release gate's Vendetta check so they cannot drift. Wathe picks a revolver target on the client
 * ({@code RevolverItem.getGunTarget} + {@code use}); the forced shot has no client, so this restates on the server
 * what SparkWitch's client selector mixins add, in the same nearest-wins order:
 * <ol>
 *   <li>Wathe's 30-block players-only ray, lengthened by an active Marksman range trait, through which active Wraiths
 *   pass (except the exact Vendetta pair), as the client Wraith revolver-aim pass-through does; a
 *   sleeping Wraith picked by Wathe's bed fallback is a miss.</li>
 *   <li>A live Magician puppet strictly nearer ({@code MagicianRevolverTargetMixin}).</li>
 *   <li>A Raven Form crow strictly nearer, never the shooter's own ({@code BlackRavenCrowRevolverAimMixin}); Wathe's
 *   receiver then resolves the crow id to its owner ({@code BlackRavenCrowGunReceiverMixin}).</li>
 *   <li>A Seeker device strictly nearer, through the side-neutral {@link SeekerDeviceRaycast} ({@code SeekerGunTargetMixin}).</li>
 * </ol>
 * Not restated: SparkStrength's client-only drone and Partygoer balloon picks (no SparkWitch seam), so the forced ray
 * passes through those. Server only.
 * 强制开枪唯一的服务端选靶，由 {@link DominatorForcedShotService#fireNow} 与释放门槛的仇杀检查共用，二者不会分歧。Wathe 在客户端
 * 选左轮目标；强制开枪没有客户端，因此这里按同样的“最近者命中”顺序在服务端重述 SparkWitch 客户端选靶 mixin 的规则：
 * 30 格仅玩家射线（精确枪手词条延长），激活的冤魂被穿透（仇杀精确配对除外），床铺回退选中睡着的冤魂视为未命中；严格更近的
 * 魔术师皮套；严格更近的化鸦乌鸦（绝不是射手自己的），随后由 Wathe 接收器解析为其主人；严格更近的搜寻者设备。未重述：
 * SparkStrength 仅客户端的无人机与派对气球选靶（SparkWitch 无接缝），强制射线会穿过它们。仅服务端。
 */
public final class DominatorForcedShotAim {
    /** Vanilla search-box growth around the ray. / 原版射线搜索箱外扩量。 */
    private static final double SEARCH_PADDING = 1.0;

    /**
     * The id sent to Wathe's receiver (-1 for a miss) and the player that id would kill, if any (a crow's owner
     * included). / 发给 Wathe 接收器的 id（未命中为 -1），以及该 id 将会击杀的玩家（含乌鸦的主人）。
     */
    public record Shot(int targetId, @Nullable ServerPlayerEntity victim) {
    }

    private DominatorForcedShotAim() {
    }

    public static Shot resolve(ServerPlayerEntity shooter) {
        double range = DominatorRules.FORCED_SHOT_RANGE
                * SparkTraitsControlExpertBridge.marksmanRangeMultiplier(shooter);
        HitResult hit = ProjectileUtil.getCollision(
                shooter,
                entity -> entity instanceof PlayerEntity player
                        && GameFunctions.isPlayerAliveAndSurvival(player)
                        && !passesThroughWraith(shooter, player),
                range
        );
        hit = preferNearer(shooter, hit, range, puppets(shooter, range));
        hit = preferNearer(shooter, hit, range, crows(shooter, range));
        hit = SeekerDeviceRaycast.preferNearerDevice(shooter, hit, range);
        World world = shooter.getWorld();
        int targetId = RevolverItem.resolveTargetFromHitResult(world, hit);
        Entity aimed = targetId < 0 ? null : world.getEntityById(targetId);
        if (!(hit instanceof EntityHitResult) && aimed instanceof PlayerEntity sleeper
                && passesThroughWraith(shooter, sleeper)) {
            // Bed fallback on a sleeping active Wraith: a plain miss, as on the client. / 床铺回退选中睡着的冤魂：按未命中处理。
            return new Shot(-1, null);
        }
        ServerPlayerEntity victim = aimed instanceof ServerPlayerEntity player
                ? player
                : aimed instanceof BlackRavenCrowEntity crow ? BlackRavenCrowSessions.ownerOf(crow) : null;
        return new Shot(targetId, victim);
    }

    /**
     * Server form of the client Wraith pass-through: another player's active Wraith, unless the two are the exact
     * Vendetta pair (the SparkFactionAPI isolation exception). / 客户端冤魂穿透的服务端形式。
     */
    static boolean passesThroughWraith(ServerPlayerEntity shooter, PlayerEntity candidate) {
        return candidate != shooter
                && WraithStateService.isActive(candidate)
                && !VendettaInteractionService.isExactPair(shooter, candidate);
    }

    private static List<? extends Entity> puppets(ServerPlayerEntity shooter, double range) {
        Box search = searchBox(shooter, range);
        return shooter.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class, search,
                puppet -> puppet.isAlive() && !puppet.isRemoved());
    }

    private static List<? extends Entity> crows(ServerPlayerEntity shooter, double range) {
        Box search = searchBox(shooter, range).expand(BlackRavenCrowHitRules.CLIENT_AIM_GROWTH);
        return shooter.getWorld().getEntitiesByClass(BlackRavenCrowEntity.class, search,
                crow -> !crow.isRemoved() && !crow.isOwnedBy(shooter.getUuid()));
    }

    private static Box searchBox(ServerPlayerEntity shooter, double range) {
        Vec3d start = shooter.getEyePos();
        return new Box(start, start.add(shooter.getRotationVec(0.0F).multiply(range))).expand(SEARCH_PADDING);
    }

    /**
     * {@code original} unless one of {@code candidates} is strictly nearer on the block-clipped look ray (crow boxes
     * grown by {@link BlackRavenCrowHitRules#CLIENT_AIM_GROWTH}, as the client picks them).
     * 除非候选中有在按方块截断的视线上严格更近者，否则返回 {@code original}。
     */
    private static HitResult preferNearer(ServerPlayerEntity shooter, HitResult original,
                                          double range, List<? extends Entity> candidates) {
        if (candidates.isEmpty() || original instanceof EntityHitResult hit && candidates.contains(hit.getEntity())) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0) || !Double.isFinite(limit)) {
            return original;
        }
        Vec3d end = clipToBlocks(shooter, start, start.add(look.multiply(limit)));
        Entity nearest = null;
        double nearestSquared = limit * limit;
        for (Entity candidate : candidates) {
            Box box = candidate instanceof BlackRavenCrowEntity
                    ? candidate.getBoundingBox().expand(BlackRavenCrowHitRules.CLIENT_AIM_GROWTH)
                    : candidate.getBoundingBox();
            double distance = BlackRavenCrowHitRules.entrySquared(start, end, List.of(box));
            // Strictly nearer: on a tie the original target keeps the hit. / 严格更近：距离相同时原目标保留命中。
            if (distance >= 0.0 && distance < nearestSquared) {
                nearest = candidate;
                nearestSquared = distance;
            }
        }
        return nearest == null ? original : new EntityHitResult(nearest);
    }

    /** Same reference distance as the client selectors. / 与客户端选靶相同的参考距离。 */
    private static double referenceDistance(Vec3d start, Vec3d look, HitResult original, double range) {
        if (original instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox();
            double reach = Math.max(range, Math.sqrt(squaredDistanceToBox(start, box)) + 2.0);
            Optional<Vec3d> entry = box.raycast(start, start.add(look.multiply(reach)));
            return entry.map(start::distanceTo).orElseGet(() -> Math.sqrt(squaredDistanceToBox(start, box)));
        }
        Vec3d position = original == null ? null : original.getPos();
        if (position != null) {
            double distance = start.distanceTo(position);
            if (Double.isFinite(distance) && distance > 0.0) {
                return distance;
            }
        }
        return range;
    }

    private static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = point.x - MathHelper.clamp(point.x, box.minX, box.maxX);
        double dy = point.y - MathHelper.clamp(point.y, box.minY, box.maxY);
        double dz = point.z - MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vec3d clipToBlocks(ServerPlayerEntity shooter, Vec3d start, Vec3d end) {
        HitResult block = shooter.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }
}
