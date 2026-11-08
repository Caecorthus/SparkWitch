package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

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
    /** The gun receivers' target distance cap. / 枪械接收器的目标距离上限。 */
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
     * centre), or, on a miss, along the server rotation clipped by the first COLLIDER block within {@code range}.
     * 左轮系一枪，自 {@code shooter} 的眼睛出发（Wathe 枪械接收器、猎魔枪、魔术师皮套）：到它进入 {@code hit} 的位置（服务端视线
     * 对其延迟补偿碰撞箱，否则为指向其碰撞箱中心的射线）；未命中时沿服务端朝向，被 {@code range} 内第一个 COLLIDER 方块截断。
     */
    public static void gunShot(@Nullable PlayerEntity shooter, @Nullable Entity hit, double range) {
        if (shooter == null || !(shooter.getWorld() instanceof ServerWorld world) || !SmokeCloudService.hasSmoke(world)) {
            return;
        }
        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0F);
        // Wathe and NoellesRoles drop a spectator or a target 65+ blocks away, so the shot is then a miss.
        // Wathe 与 NoellesRoles 会丢弃旁观者或 65 格外的目标，此时这一枪按未命中处理。
        boolean hits = hit != null && hit != shooter && !hit.isSpectator()
                && hit.squaredDistanceTo(shooter) < MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
        Vec3d end = hits ? entryPoint(shooter, hit, eye, look)
                : blockClipped(world, shooter, eye, eye.add(look.multiply(range)));
        tunnel(world, eye, end, BearRicochetRules.TUNNEL_RADIUS, shooter.getUuid());
    }

    /** {@link #gunShot} for a payload's target entity id. / 以数据包目标实体 id 调用 {@link #gunShot}。 */
    public static void gunShot(@Nullable ServerPlayerEntity shooter, int targetId, double range) {
        if (shooter != null) {
            gunShot(shooter, shooter.getServerWorld().getEntityById(targetId), range);
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
