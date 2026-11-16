package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.compat.SparkTraitsSaviorBridge;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudService;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one-line call sites that let ranged fire carve BEAR smoke (S3 + bullet tunnels): every hitscan ray carves a
 * {@link BearRicochetRules#TUNNEL_RADIUS} capsule ({@code SmokeCloudService.carveTunnel}), every blast a hole
 * ({@code carveHole}). Purely visual (smoke never feeds a server rule) and cheap when no smoke exists: the smoke service
 * returns early. Server thread only; a client world or a degenerate segment is ignored. SparkStrength's M67 and drone
 * use the public {@code SparkWitchApi.carveSmoke} instead.
 * 让远程攻击在 BEAR 烟雾中开洞的一行式调用点（S3 + 弹道通道）：每条命中扫描射线开出半径 {@link BearRicochetRules#TUNNEL_RADIUS}
 * 的胶囊（{@code SmokeCloudService.carveTunnel}），每次爆炸开一个洞（{@code carveHole}）。纯视觉（烟雾从不影响服务端规则），
 * 没有烟时开销极小：烟雾服务会提前返回。仅限服务端线程；客户端世界或退化线段会被忽略。SparkStrength 的 M67 与无人机改用公开的
 * {@code SparkWitchApi.carveSmoke}。
 */
public final class RangedSmokeCarve {
    /**
     * The gun receivers' base target distance cap; SparkTraits Niko widens it per shot (see the four-argument
     * {@link #gunShot(PlayerEntity, Entity, double, double)}). / 枪械接收器的基础目标距离上限；SparkTraits Niko 会按每次射击
     * 放宽它（见四参数的 {@link #gunShot(PlayerEntity, Entity, double, double)}）。
     */
    static final double MAX_TARGET_DISTANCE = 65.0;

    private RangedSmokeCarve() {
    }

    /** One hitscan ray. / 一条命中扫描射线。 */
    public static void ray(@Nullable World world, @Nullable Vec3d from, @Nullable Vec3d to) {
        tunnel(world, from, to, BearRicochetRules.TUNNEL_RADIUS);
    }

    /** A segment with its own radius (the potion backblast lane). / 自定半径的线段（药炮尾焰通道）。 */
    public static void tunnel(@Nullable World world, @Nullable Vec3d from, @Nullable Vec3d to, float radius) {
        tunnel(world, from, to, radius, null);
    }

    /**
     * {@link #tunnel} with the shooter known, so the smoke service's per-shooter rate limit follows a moving shooter.
     * 已知射手的 {@link #tunnel}，使烟雾服务的按射手限流能跟上移动中的射手。
     */
    private static void tunnel(@Nullable World world, @Nullable Vec3d from, @Nullable Vec3d to, float radius,
                               @Nullable UUID shooter) {
        if (world instanceof ServerWorld server && finite(from) && finite(to) && from.squaredDistanceTo(to) > 1.0E-6) {
            SmokeCloudService.carveTunnel(server, from, to, radius, shooter);
        }
    }

    /** A bullet polyline (the AXMC's penetrating path). / 子弹折线（AXMC 的穿透路径）。 */
    public static void path(@Nullable World world, @Nullable List<Vec3d> points) {
        if (points == null) {
            return;
        }
        for (int index = 1; index < points.size(); index++) {
            ray(world, points.get(index - 1), points.get(index));
        }
    }

    /** A blast hole of {@code radius} blocks. / 半径 {@code radius} 格的爆炸空洞。 */
    public static void blast(@Nullable World world, @Nullable Vec3d centre, double radius) {
        if (world instanceof ServerWorld server && finite(centre) && Double.isFinite(radius) && radius > 0.0) {
            SmokeCloudService.carveHole(server, centre, (float) radius);
        }
    }

    /**
     * A revolver-family shot from {@code shooter}'s eyes (Wathe gun receiver, Demon Hunter, the Magician puppet): to
     * where it entered {@code hit} (the server look ray against its lag-compensated boxes, else the ray toward its box
     * centre), or, on a miss, along the server rotation clipped by the first COLLIDER block within {@code range}. The
     * target cap is the receivers' plain 65 blocks.
     * 左轮系一枪，自 {@code shooter} 的眼睛出发（Wathe 枪械接收器、猎魔枪、魔术师皮套）：到它进入 {@code hit} 的位置（服务端视线
     * 对其延迟补偿碰撞箱，否则为指向其碰撞箱中心的射线）；未命中时沿服务端朝向，被 {@code range} 内第一个 COLLIDER 方块截断。
     * 目标上限为接收器原本的 65 格。
     */
    public static void gunShot(@Nullable PlayerEntity shooter, @Nullable Entity hit, double range) {
        gunShot(shooter, hit, range, MAX_TARGET_DISTANCE);
    }

    /**
     * {@link #gunShot(PlayerEntity, Entity, double)} with the target cap the receiver really used for this shot, so the
     * tunnel follows the real bullet: {@code targetRange} is that receiver's widen-only cap (SparkTraits Niko: 300
     * blocks; never below 65, and 65 for a non-finite value). A widened cap is also that shooter's client pick range,
     * so a miss then runs that far instead of {@code range}. The miss ray is clipped by blocks only as far as live
     * smoke reaches along it ({@code SmokeCloudService.tunnelReach}), so a 300-block shot never raycasts past the last
     * cloud it could carve, and not at all when it crosses none.
     * 带上接收器对这一枪实际使用的目标上限的 {@link #gunShot(PlayerEntity, Entity, double)}，使通道跟随真实弹道：
     * {@code targetRange} 是该接收器只放宽不收紧的上限（SparkTraits Niko：300 格；不低于 65，非有限值按 65）。被放宽的上限同时是
     * 该射手的客户端选靶距离，因此未命中时按它而不是 {@code range} 延伸。未命中射线只在沿途存活烟雾所及的范围内被方块截断
     * （{@code SmokeCloudService.tunnelReach}），因此 300 格的一枪不会越过它能开洞的最后一团烟做射线检测，不穿过任何烟时完全不做。
     */
    public static void gunShot(@Nullable PlayerEntity shooter, @Nullable Entity hit, double range, double targetRange) {
        if (shooter == null || !(shooter.getWorld() instanceof ServerWorld world) || !SmokeCloudService.hasSmoke(world)) {
            return;
        }
        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0F);
        double cap = Double.isFinite(targetRange) && targetRange > MAX_TARGET_DISTANCE
                ? targetRange : MAX_TARGET_DISTANCE;
        // Wathe and NoellesRoles drop a spectator or a target beyond the cap, so the shot is then a miss.
        // Wathe 与 NoellesRoles 会丢弃旁观者或超出上限的目标，此时这一枪按未命中处理。
        boolean hits = hit != null && hit != shooter && !hit.isSpectator()
                && hit.squaredDistanceTo(shooter) < cap * cap;
        Vec3d end;
        if (hits) {
            end = entryPoint(shooter, hit, eye, look);
        } else {
            double reach = SmokeCloudService.tunnelReach(world, eye,
                    eye.add(look.multiply(cap > MAX_TARGET_DISTANCE ? Math.max(range, cap) : range)),
                    BearRicochetRules.TUNNEL_RADIUS);
            if (!(reach > 0.0)) {
                return;
            }
            end = blockClipped(world, shooter, eye, eye.add(look.multiply(reach)));
        }
        tunnel(world, eye, end, BearRicochetRules.TUNNEL_RADIUS, shooter.getUuid());
    }

    /** {@link #gunShot} for a payload's target entity id. / 以数据包目标实体 id 调用 {@link #gunShot}。 */
    public static void gunShot(@Nullable ServerPlayerEntity shooter, int targetId, double range) {
        if (shooter != null) {
            gunShot(shooter, shooter.getServerWorld().getEntityById(targetId), range);
        }
    }

    /**
     * {@link #gunShot} for Wathe's gun receiver, by the payload's target entity id. SparkTraits widens that receiver's
     * 65-block cap for a sneaking Niko holder (its own {@code @ModifyConstant}); the same value is read here through
     * the public facade ({@link SparkTraitsSaviorBridge}: widen-only, 65 when SparkTraits is absent or fails), for the
     * gun in the main hand, as the receiver does. Asked only while the world has smoke.
     * 以数据包目标实体 id 为 Wathe 枪械接收器调用 {@link #gunShot}。SparkTraits 会为潜行的 Niko 持有者放宽该接收器的 65 格上限
     * （它自己的 {@code @ModifyConstant}）；这里经公共门面读取同一数值（{@link SparkTraitsSaviorBridge}：只放宽，SparkTraits
     * 缺失或出错时为 65），与接收器一样按主手的枪查询。只在世界中有烟时才查询。
     */
    public static void widenedGunShot(@Nullable ServerPlayerEntity shooter, int targetId, double range) {
        if (shooter != null && SmokeCloudService.hasSmoke(shooter.getServerWorld())) {
            gunShot(shooter, shooter.getServerWorld().getEntityById(targetId), range,
                    SparkTraitsSaviorBridge.pistolServerRange(shooter, shooter.getMainHandStack().getItem(),
                            MAX_TARGET_DISTANCE));
        }
    }

    private static Vec3d entryPoint(PlayerEntity shooter, Entity hit, Vec3d eye, Vec3d look) {
        double reach = eye.distanceTo(hit.getBoundingBox().getCenter()) + hit.getWidth() + 2.0;
        Vec3d far = eye.add(look.multiply(reach));
        List<Box> volumes = shooter instanceof ServerPlayerEntity server && hit instanceof PlayerEntity target
                ? PlayerHitboxHistory.hitVolumes(server, target, 0.0) : List.of();
        Vec3d best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Box box : volumes.isEmpty() ? List.of(hit.getBoundingBox()) : volumes) {
            Optional<Vec3d> entry = box.contains(eye) ? Optional.of(eye) : box.raycast(eye, far);
            if (entry.isPresent() && eye.squaredDistanceTo(entry.get()) < bestDistance) {
                best = entry.get();
                bestDistance = eye.squaredDistanceTo(best);
            }
        }
        if (best != null) {
            return best;
        }
        Vec3d centre = hit.getBoundingBox().getCenter();
        return hit.getBoundingBox().raycast(eye, centre.add(centre.subtract(eye))).orElse(centre);
    }

    private static Vec3d blockClipped(ServerWorld world, Entity shooter, Vec3d eye, Vec3d end) {
        HitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    private static boolean finite(@Nullable Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
