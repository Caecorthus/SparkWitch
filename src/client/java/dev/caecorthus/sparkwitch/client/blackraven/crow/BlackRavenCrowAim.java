package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHitRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Client weapon targeting against Raven Form crows (Wathe revolver, derringer, knife and the gun crosshair; through
 * {@code getGunTarget} also SparkStrength's Serial Killer pistols, and through {@code getKnifeTarget} the Swordfish and
 * the instant stabs). The selectors keep whatever they returned (a player, a Magician puppet, a Seeker device later,
 * the Wraith pass-through, a SparkTraits Marksman range) unless a crow on the same look ray is strictly nearer than
 * that result, entity or block; then the crow's {@link EntityHitResult} is returned and the caller sends its id. The
 * ray mirrors {@code ProjectileUtil}'s collision helper (eye, {@code getRotationVec(0)}, cut at the first COLLIDER
 * block), with crow boxes grown by {@link BlackRavenCrowHitRules#CLIENT_AIM_GROWTH} because the crow is a small, fast
 * target. The local player's own crow is never picked. Client prediction only: the server re-checks every crow id on
 * lag-compensated volumes ({@code BlackRavenCrowHits}).
 * 客户端武器对化鸦乌鸦的选靶（Wathe 左轮、德林加、刀与枪械准星；经 {@code getGunTarget} 同时覆盖 SparkStrength 连环杀手手枪，
 * 经 {@code getKnifeTarget} 覆盖剑鱼与各种瞬刺）。选靶器保留其原有结果（玩家、魔术师皮套、之后的搜寻者设备、冤魂穿透、
 * SparkTraits 神射手射程），除非同一视线上有乌鸦严格近于该结果（实体或方块），此时返回乌鸦的 {@link EntityHitResult}，
 * 调用方便发送其 id。射线与 {@code ProjectileUtil} 的碰撞辅助一致（眼睛位置、{@code getRotationVec(0)}、在第一个 COLLIDER
 * 方块处截断），乌鸦箱体按 {@link BlackRavenCrowHitRules#CLIENT_AIM_GROWTH} 扩大，因为乌鸦又小又快。本地玩家自己的乌鸦永远
 * 不会被选中。仅为客户端预测：服务端会以延迟补偿体积复查每个乌鸦 id（{@code BlackRavenCrowHits}）。
 */
public final class BlackRavenCrowAim {
    /** Wathe {@code RevolverItem.getGunTarget} ray length. / Wathe 左轮选靶射线长度。 */
    public static final double REVOLVER_RANGE = 30.0;
    /** Wathe {@code DerringerItem.getGunTarget} ray length. / Wathe 德林加选靶射线长度。 */
    public static final double DERRINGER_RANGE = 7.0;
    /** Wathe {@code KnifeItem.getKnifeTarget} ray length. / Wathe 刀选靶射线长度。 */
    public static final double KNIFE_RANGE = 3.0;
    /** Vanilla search-box growth around the ray. / 原版射线搜索箱外扩量。 */
    private static final double SEARCH_PADDING = 1.0;

    private BlackRavenCrowAim() {
    }

    /**
     * {@code original} unless a crow is strictly nearer on the shooter's look ray. Server-side calls return
     * {@code original}. / 除非射手视线上有严格更近的乌鸦，否则返回 {@code original}。服务端调用原样返回。
     */
    public static HitResult preferNearerCrow(@Nullable PlayerEntity shooter, HitResult original, double range) {
        if (shooter == null || original == null || !shooter.getWorld().isClient()
                || original instanceof EntityHitResult hit && hit.getEntity() instanceof BlackRavenCrowEntity) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0) || !Double.isFinite(limit)) {
            return original;
        }
        Vec3d end = start.add(look.multiply(limit));
        HitResult block = shooter.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        BlackRavenCrowEntity nearest = null;
        double nearestDistance = limit * limit;
        for (BlackRavenCrowEntity crow : shooter.getWorld().getEntitiesByClass(BlackRavenCrowEntity.class,
                new Box(start, end).expand(SEARCH_PADDING + BlackRavenCrowHitRules.CLIENT_AIM_GROWTH),
                candidate -> isTargetable(candidate, shooter))) {
            double distance = BlackRavenCrowHitRules.entrySquared(start, end,
                    List.of(crow.getBoundingBox().expand(BlackRavenCrowHitRules.CLIENT_AIM_GROWTH)));
            // Strictly nearer: on a tie the original target keeps the hit. / 严格更近：距离相同时原目标保留命中。
            if (distance >= 0.0 && distance < nearestDistance) {
                nearest = crow;
                nearestDistance = distance;
            }
        }
        return nearest == null ? original : new EntityHitResult(nearest);
    }

    static boolean isTargetable(BlackRavenCrowEntity crow, PlayerEntity shooter) {
        return !crow.isRemoved() && !crow.isOwnedBy(shooter.getUuid());
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
            double reach = Math.max(range, Math.sqrt(box.squaredMagnitude(start)) + 2.0);
            Optional<Vec3d> entry = box.raycast(start, start.add(look.multiply(reach)));
            return entry.map(start::distanceTo).orElseGet(() -> Math.sqrt(box.squaredMagnitude(start)));
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
}
