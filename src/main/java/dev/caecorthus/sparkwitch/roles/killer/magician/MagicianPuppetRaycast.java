package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Nearest-wins geometry for Magician puppets, the puppet counterpart of the Seeker's {@code SeekerDeviceRaycast}. A
 * puppet is measured exactly like the player it copies (the box growth the weapon gives players), rays stop at the
 * first COLLIDER block, and only a STRICTLY nearer puppet takes a hit (a tie goes to the real player or device). The
 * server helpers also see Seeker devices the attacker may break, so a puppet never steals a hit a nearer device would
 * absorb and never ends behind one; the caller then skips the Seeker hook whenever a puppet took the hit.
 * 魔术师皮套的“最近者命中”几何，对应搜寻者的 {@code SeekerDeviceRaycast}。皮套与其复制的玩家按完全相同的方式量取（武器给
 * 玩家的箱体扩大量），射线止于第一个 COLLIDER 方块，只有严格更近的皮套才承受命中（距离相同时归真实玩家或设备）。服务端
 * 辅助方法同时考虑攻击者可打坏的搜寻者设备，因此皮套既不会抢走更近设备本应吸收的命中，也不会在设备之后被结束；皮套承受
 * 命中时由调用方跳过搜寻者钩子。
 */
final class MagicianPuppetRaycast {
    /** Search-box padding around a segment. / 线段搜索箱的外扩量。 */
    private static final double SEARCH_PADDING = 1.0;

    private MagicianPuppetRaycast() {
    }

    /** A puppet on a segment: entry point and squared distance from the start. / 线段上的皮套：入射点与到起点的平方距离。 */
    record PuppetHit(MagicianPlaybackEntity puppet, Vec3d point, double distanceSquared) {
    }

    // ---- Server helpers ----

    /**
     * The nearest live puppet {@code attacker} may hit ({@link MagicianPuppetHits#hittableBy}) whose box, grown by
     * {@code growth}, meets {@code start → end} (already block-clipped) strictly nearer than {@code beatSquared}.
     * {@code attacker} 可命中的最近活皮套：其按 {@code growth} 扩大后的箱体与（已按方块截断的）线段相交，且严格近于
     * {@code beatSquared}。
     */
    @Nullable
    static PuppetHit nearestHittable(ServerPlayerEntity attacker, Vec3d start, Vec3d end, double growth,
                                     double beatSquared) {
        if (!(attacker.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        List<MagicianPlaybackEntity> candidates = MagicianPlaybackManager.livePuppets(world).stream()
                .filter(puppet -> MagicianPuppetHits.hittableBy(attacker, puppet))
                .toList();
        return nearest(start, end, candidates, growth, beatSquared);
    }

    /**
     * The nearest puppet among {@code candidates} by {@link #pick}, with its entry point and squared distance; boxes
     * grow by {@code growth}. / 按 {@link #pick} 选出的最近皮套，附入射点与平方距离；箱体按 {@code growth} 扩大。
     */
    @Nullable
    static PuppetHit nearest(Vec3d start, Vec3d end, Iterable<MagicianPlaybackEntity> candidates, double growth,
                             double beatSquared) {
        double grow = Math.max(0.0, growth);
        MagicianPlaybackEntity selected = pick(start, end, candidates,
                candidate -> candidate.getBoundingBox().expand(grow), beatSquared);
        if (selected == null) {
            return null;
        }
        Box box = selected.getBoundingBox().expand(grow);
        Vec3d point = box.contains(start) ? start : box.raycast(start, end).orElse(start);
        return new PuppetHit(selected, point, start.squaredDistanceTo(point));
    }

    /**
     * Pure pick: the candidate whose box is entered first along {@code start → end}, strictly nearer than
     * {@code beatSquared} (a tie keeps the earlier candidate, and loses to the beat); a box containing {@code start} is
     * at distance 0.
     * 纯选择：沿线段最先进入其箱体、且严格近于 {@code beatSquared} 的候选（距离相同时保留先出现者，且输给 beat）；
     * 包含起点的箱体距离为 0。
     */
    @Nullable
    static <T> T pick(Vec3d start, Vec3d end, Iterable<? extends T> candidates, Function<? super T, Box> boxOf,
                      double beatSquared) {
        T selected = null;
        double closest = beatSquared;
        for (T candidate : candidates) {
            double distance = entrySquared(start, end, boxOf.apply(candidate));
            if (distance >= 0.0 && puppetWins(distance, closest)) {
                closest = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    /**
     * Squared ray entry of the nearest Seeker device {@code attacker} may break on {@code start → end}, using the
     * device rays' own filter (not the attacker's own device, {@link SeekerDamageRules#mayBreak} with the owner as
     * proxy) and margin-grown box; positive infinity when none. A device the attacker may not break is transparent to
     * its server rays, so it shields no puppet either.
     * {@code start → end} 上 {@code attacker} 可打坏的最近搜寻者设备的射线入射平方距离，沿用设备射线自身的过滤（非攻击者
     * 自己的设备，以拥有者为代理的 {@link SeekerDamageRules#mayBreak}）与扩大余量后的箱体；没有时为正无穷。攻击者不能打坏的
     * 设备对其服务端射线透明，因此也挡不住皮套。
     */
    static double breakableDeviceSquared(ServerPlayerEntity attacker, Vec3d start, Vec3d end) {
        World world = attacker.getWorld();
        List<SeekerDeviceEntity> devices = world.getEntitiesByClass(SeekerDeviceEntity.class,
                new Box(start, end).expand(SEARCH_PADDING), device -> device.isAlive() && !device.isRemoved());
        if (devices.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        double nearest = Double.POSITIVE_INFINITY;
        for (SeekerDeviceEntity device : devices) {
            double distance = entrySquared(start, end,
                    device.getBoundingBox().expand(Math.max(0.0, device.targetingMargin())));
            if (distance >= 0.0 && distance < nearest && !SeekerDeviceRaycast.isOwnDevice(attacker, device)
                    && SeekerDamageRules.mayBreak(attacker, SeekerDeviceService.findOwner(device), game)) {
                nearest = distance;
            }
        }
        return nearest;
    }

    /**
     * Squared distance to a player target on the ray: where it enters the target's box grown by {@code growth}, else
     * its closest point (a lag-compensated pick may miss the current box); positive infinity without a target.
     * 射线上到玩家目标的平方距离：进入其按 {@code growth} 扩大的箱体处，否则取最近点（延迟补偿的选择可能不与当前箱体相交）；
     * 没有目标时为正无穷。
     */
    static double playerDistanceSquared(Vec3d start, Vec3d end, @Nullable Entity target, double growth) {
        if (target == null) {
            return Double.POSITIVE_INFINITY;
        }
        Box box = target.getBoundingBox().expand(Math.max(0.0, growth));
        double entry = entrySquared(start, end, box);
        return entry >= 0.0 ? entry : SeekerDamageRules.squaredDistanceToBox(start, box);
    }

    /** Pure nearest-wins comparison: a puppet must be strictly nearer. / 纯“最近者命中”比较：皮套必须严格更近。 */
    static boolean puppetWins(double puppetSquared, double beatSquared) {
        return puppetSquared < beatSquared;
    }

    /** Squared distance to where the segment enters {@code box}; 0 inside, -1 on a miss. / 未命中返回 -1。 */
    static double entrySquared(Vec3d start, Vec3d end, Box box) {
        return HitscanLagRules.entryDistanceSquared(start, end, List.of(box));
    }

    /** Cuts {@code end} at the first COLLIDER block. / 在第一个 COLLIDER 方块处截断终点。 */
    static Vec3d clipToBlocks(World world, Vec3d start, Vec3d end, Entity context) {
        HitResult block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, context));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }
}
