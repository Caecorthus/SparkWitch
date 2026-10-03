package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Whether a projectile may leave through a destination gate. Pure: the world is reached only through {@link Probe}, so
 * the rule is unit-tested against a fake world. An exit is usable only when
 * <ul>
 *     <li>the exit point is entity-ticking (a projectile moved into a non-ticking section would freeze);</li>
 *     <li>for seam projectiles that still move this tick, the compensated start ({@code exit - velocity}, behind the
 *     destination) is entity-ticking too, so no destination needs an uncollided "start at the exit" move;</li>
 *     <li>the projectile's box at the exit overlaps no block: vanilla collision ignores voxels a box already overlaps,
 *     so a box placed in a wall would cross it, and a pearl would land its thrower inside it;</li>
 *     <li>a COLLIDER line from the gate centre to the exit box centre meets no block (a pane or a closed door between
 *     the slab and the exit).</li>
 * </ul>
 * A gate that fails is skipped; with no gate left the projectile reflects.
 * 投掷物能否从某目标门离开。纯逻辑：只经 {@link Probe} 访问世界，因此可用假世界做单元测试。只有同时满足以下条件的出口才可用：
 * 出口点处于实体 tick 范围（移入不 tick 区段的投掷物会冻结）；对本刻仍会移动的接缝投掷物，补偿起点（{@code exit - velocity}，
 * 位于目标门之后）也处于实体 tick 范围，因此任何目标门都不需要无碰撞的“从出口起步”移动；投掷物在出口处的碰撞箱不与任何方块
 * 重叠（原版碰撞会忽略箱体已重叠的体素，放进墙里的箱体会穿墙，珍珠会把投掷者传进墙里）；从门中心到出口箱体中心的 COLLIDER
 * 连线不碰到方块（门板与出口之间的玻璃板或关着的门）。不满足的门被跳过；没有剩余的门时投掷物反弹。
 */
public final class RiftProjectileExitRules {
    private RiftProjectileExitRules() {
    }

    /**
     * World queries the rule needs; the server implements them over its {@code ServerWorld}.
     * 规则所需的世界查询；服务端基于 {@code ServerWorld} 实现。
     */
    public interface Probe {
        /** The point's chunk ticks entities. / 该点所在区块会 tick 实体。 */
        boolean entityTicking(Vec3d point);

        /** No block (or collidable entity, or world border) collision in the box. / 箱体内没有方块（可碰撞实体、世界边界）碰撞。 */
        boolean spaceEmpty(Box box);

        /** A COLLIDER line between the points meets no block. / 两点之间的 COLLIDER 连线不碰到方块。 */
        boolean clearLine(Vec3d from, Vec3d to);
    }

    /**
     * @param destinationPos      the destination gate's position (bottom centre of its slab) / 目标门位置（门板底面中心）
     * @param exitPosition        entity position at the exit / 出口处的实体位置
     * @param exitVelocity        velocity after the pass / 穿门后的速度
     * @param movesAgainThisTick  true for seam projectiles (vanilla still moves them by their velocity this tick)
     *                            / 接缝投掷物为 true（原版本刻还会按速度移动它们）
     */
    public static boolean usable(Probe probe, Vec3d destinationPos, Vec3d exitPosition, Vec3d exitVelocity,
                                 double projectileWidth, double projectileHeight, boolean movesAgainThisTick) {
        if (probe == null || destinationPos == null || exitPosition == null || exitVelocity == null
                || !probe.entityTicking(exitPosition)) {
            return false;
        }
        if (movesAgainThisTick
                && !probe.entityTicking(RiftProjectileMath.sameTickMoveStart(exitPosition, exitVelocity))) {
            return false;
        }
        Box box = RiftProjectileMath.boxAt(exitPosition, projectileWidth, projectileHeight);
        return probe.spaceEmpty(box) && probe.clearLine(RiftProjectileMath.gateCentre(destinationPos), box.getCenter());
    }
}
