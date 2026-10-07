package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Client weapon targeting against Magician puppets (Wathe revolver, derringer, knife, the gun crosshair, and the
 * NoellesRoles Demon Hunter pistol). The selectors keep whatever they returned (Wathe's pick, a SparkTraits Marksman
 * replacement, the Wraith pass-through) unless a live puppet on the same look ray is strictly nearer than that result,
 * entity or block; then the puppet's {@link EntityHitResult} is returned and the caller sends its id. The ray mirrors
 * {@code ProjectileUtil}'s collision helper: eye position, {@code getRotationVec(0)}, cut at the first COLLIDER block,
 * puppet boxes without margin, nearest box entry. Its length is the original result's distance (a miss carries the
 * selector's own range, so an extended Marksman range is kept); {@code range} is only the fallback. Client only: the
 * server validates the id ({@code MagicianPuppetHits}).
 * 客户端武器对魔术师皮套的选靶（Wathe 左轮、德林加、刀、枪械准星以及 NoellesRoles 猎魔枪）。选靶器保留其原有结果（Wathe
 * 选取、SparkTraits 神射手替换、冤魂穿透），除非同一视线上有存活皮套严格近于该结果（实体或方块），此时返回皮套的
 * {@link EntityHitResult}，调用方便发送其 id。射线与 {@code ProjectileUtil} 的碰撞辅助一致：眼睛位置、
 * {@code getRotationVec(0)}、在第一个 COLLIDER 方块处截断、皮套箱体不加余量、取最早进入的箱体。射线长度取原结果的距离
 * （未命中时即选靶器自身射程，因此保留神射手的延长射程）；{@code range} 仅作兜底。仅客户端：服务端校验该 id
 * （{@code MagicianPuppetHits}）。
 */
public final class MagicianPuppetAim {
    /** Wathe {@code RevolverItem.getGunTarget} ray length. / Wathe 左轮选靶射线长度。 */
    public static final double REVOLVER_RANGE = 30.0;
    /** Wathe {@code DerringerItem.getGunTarget} ray length. / Wathe 德林加选靶射线长度。 */
    public static final double DERRINGER_RANGE = 7.0;
    /** Wathe {@code KnifeItem.getKnifeTarget} ray length. / Wathe 刀选靶射线长度。 */
    public static final double KNIFE_RANGE = 3.0;
    /** Vanilla search-box growth around the ray. / 原版射线搜索箱外扩量。 */
    private static final double SEARCH_PADDING = 1.0;

    private MagicianPuppetAim() {
    }

    /**
     * {@code original} unless a live puppet is strictly nearer on the shooter's look ray. {@code visibleOnly} skips
     * invisible puppets, as Wathe's gun crosshair skips invisible players. Server-side calls return {@code original}.
     * 除非射手视线上有严格更近的存活皮套，否则返回 {@code original}。{@code visibleOnly} 跳过隐身皮套，与 Wathe 枪械准星
     * 跳过隐身玩家一致。服务端调用原样返回。
     */
    public static HitResult preferNearerPuppet(@Nullable PlayerEntity shooter, HitResult original, double range,
                                               boolean visibleOnly) {
        if (shooter == null || original == null || !shooter.getWorld().isClient()
                || original instanceof EntityHitResult hit && hit.getEntity() instanceof MagicianPlaybackEntity) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0) || !Double.isFinite(limit)) {
            return original;
        }
        World world = shooter.getWorld();
        Vec3d end = clipToBlocks(shooter, start, start.add(look.multiply(limit)));
        Box search = new Box(start, end).expand(SEARCH_PADDING);
        MagicianPlaybackEntity nearest = null;
        double nearestDistance = limit * limit;
        for (MagicianPlaybackEntity puppet : world.getEntitiesByClass(MagicianPlaybackEntity.class, search,
                candidate -> isTargetable(candidate, visibleOnly))) {
            Optional<Vec3d> entry = puppet.getBoundingBox().raycast(start, end);
            if (entry.isEmpty()) {
                continue;
            }
            double distance = start.squaredDistanceTo(entry.get());
            // Strictly nearer: on a tie the original target keeps the hit. / 严格更近：距离相同时原目标保留命中。
            if (distance < nearestDistance) {
                nearest = puppet;
                nearestDistance = distance;
            }
        }
        return nearest == null ? original : new EntityHitResult(nearest);
    }

    static boolean isTargetable(MagicianPlaybackEntity puppet, boolean visibleOnly) {
        return puppet.isAlive() && !puppet.isRemoved() && !(visibleOnly && puppet.isInvisible());
    }

    /**
     * Distance to the original target: where the ray enters an entity's box (Wathe's {@code EntityHitResult} carries
     * the feet position), else the block or miss position; {@code range} when neither is usable.
     * 原目标的距离：实体取射线进入其箱体处（Wathe 的 {@code EntityHitResult} 位置为脚下），否则取方块或未命中位置；
     * 两者都不可用时取 {@code range}。
     */
    static double referenceDistance(Vec3d start, Vec3d look, HitResult original, double range) {
        if (original instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox();
            double reach = Math.max(range, Math.sqrt(squaredDistanceToBox(start, box)) + 2.0);
            Optional<Vec3d> entry = box.raycast(start, start.add(look.multiply(reach)));
            return entry.map(start::distanceTo).orElseGet(() -> Math.sqrt(squaredDistanceToBox(start, box)));
        }
        Vec3d position = original.getPos();
        if (position != null) {
            double distance = start.distanceTo(position);
            if (Double.isFinite(distance) && distance > 0.0) {
                return distance;
            }
        }
        return range;
    }

    static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = point.x - MathHelper.clamp(point.x, box.minX, box.maxX);
        double dy = point.y - MathHelper.clamp(point.y, box.minY, box.maxY);
        double dz = point.z - MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vec3d clipToBlocks(PlayerEntity shooter, Vec3d start, Vec3d end) {
        HitResult block = shooter.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }
}
