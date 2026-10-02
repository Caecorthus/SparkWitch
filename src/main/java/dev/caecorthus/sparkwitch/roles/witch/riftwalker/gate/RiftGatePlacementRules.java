package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.OptionalDouble;

/**
 * Pure placement geometry and validation for a Rift Gate at the user's feet (plan §5.1): facing snapped to the four
 * horizontal directions, spacing from other gates ({@code RiftwalkerRules.MIN_GATE_SPACING}), play-area and floor
 * rules, and the Riftwalker-owned copy of the Seeker camera neighbour table. Unit-testable without Minecraft
 * registries (records, ints, doubles, enums only). Owned by P1.
 * 在使用者脚下放置裂隙门的纯几何与校验规则（plan §5.1）：朝向吸附到四个水平方向、与其他门的间距
 * （{@code RiftwalkerRules.MIN_GATE_SPACING}）、play area 与地面规则，以及复制到本职业的搜寻者摄像头邻居表。
 * 无需 Minecraft 注册表即可单元测试（仅使用 record、int、double、enum）。归属 P1。
 *
 * <p>Geometry contract: the gate position is the bottom centre of a {@code GATE_WIDTH × GATE_HEIGHT × GATE_DEPTH}
 * slab whose thin side lies along the facing axis. Along the width axis the position snaps to the centre of the block
 * column the player stands in, so a 1-wide corridor always fits; along the facing axis it keeps the player's
 * coordinate. The world-reading checks (block collisions, fluids, entities) live in {@link RiftGatePlacementService};
 * the forbidden-neighbour block table lives in {@link RiftGateNeighbourRules}.
 * 几何契约：门的位置是 {@code GATE_WIDTH × GATE_HEIGHT × GATE_DEPTH} 薄板的底部中心，薄边沿朝向轴。宽度轴上位置吸附到
 * 玩家所在方块列的中心，因此 1 格宽的走廊总能放下；朝向轴上保留玩家坐标。读取世界的检查（方块碰撞、流体、实体）位于
 * {@link RiftGatePlacementService}；禁放邻居方块表位于 {@link RiftGateNeighbourRules}。
 */
public final class RiftGatePlacementRules {
    /** Collision checks shrink boxes by this much so faces that merely touch never count. / 碰撞检查时的收缩余量。 */
    public static final double CONTACT_EPSILON = 1.0E-4;
    /** Height of the probe under the footprint that must hit something solid. / 脚下支撑探测盒的高度。 */
    public static final double SUPPORT_PROBE_HEIGHT = 0.1;
    /** The floor ray starts this far above the feet. / 地面射线从脚上方这么高处开始。 */
    public static final double FLOOR_PROBE_LIFT = 0.5;
    /** The floor may lie at most this far below the feet (Seeker car fallback drop). / 地面最多低于脚下这么多。 */
    public static final double FLOOR_DROP = 1.5;
    /** The gate base must be at least this far above {@code playArea.minY} (below it Wathe kills). / 门底高于 minY 的最小距离。 */
    public static final double MIN_BASE_ABOVE_PLAY_AREA = 1.0;
    /**
     * Wathe's {@code AlwaysVisibleFrustum} culls every box whose centre is at y ≥ 148 while the train moves, so the
     * gate's visibility box centre must stay below it.
     * 列车行驶时 Wathe 的 {@code AlwaysVisibleFrustum} 会剔除中心 y ≥ 148 的包围盒，因此门的可见包围盒中心必须低于它。
     */
    public static final double CULL_CENTRE_Y = 148.0;
    /** Visibility box (model B: 2.25 tall plus the floor rune ring) around the position. / 可见包围盒（覆盖模型 B）。 */
    public static final double VISIBILITY_HALF_WIDTH = 0.75;
    public static final double VISIBILITY_BELOW = 0.25;
    public static final double VISIBILITY_ABOVE = 2.5;
    /** Minimum clearance between the gate box and any Seeker device box. / 门与搜寻者设备包围盒之间的最小间隙。 */
    public static final double DEVICE_CLEARANCE = 1.0;
    /** Forbidden neighbours are searched this many blocks around the gate box. / 禁放邻居的搜索半径（格）。 */
    public static final double NEIGHBOUR_RADIUS = 1.0;
    /** Depth of the standing cell that must stay clear in front of the gate (C15). / 门正前方须保持空旷的站立格深度（C15）。 */
    public static final double FRONT_CLEARANCE_DEPTH = 1.0;

    private RiftGatePlacementRules() {
    }

    // ---- Facing and box / 朝向与包围盒 ----

    /** The gate's front faces where the player looks (yaw 0 = south). / 门的正面朝向玩家视线方向（yaw 0 = 南）。 */
    public static Direction facingFromYaw(float yaw) {
        return Direction.fromRotation(yaw);
    }

    /** Non-horizontal input becomes NORTH, matching {@code RiftGateEntity.setFacing}. / 非水平方向视为北。 */
    public static Direction horizontal(Direction facing) {
        return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }

    /** Half extent along x: thin when the gate faces east/west. / x 方向半宽：朝东/西时为薄边。 */
    public static double halfExtentX(Direction facing) {
        return horizontal(facing).getAxis() == Direction.Axis.X
                ? RiftwalkerRules.GATE_DEPTH / 2.0
                : RiftwalkerRules.GATE_WIDTH / 2.0;
    }

    /** Half extent along z: thin when the gate faces north/south. / z 方向半宽：朝北/南时为薄边。 */
    public static double halfExtentZ(Direction facing) {
        return horizontal(facing).getAxis() == Direction.Axis.Z
                ? RiftwalkerRules.GATE_DEPTH / 2.0
                : RiftwalkerRules.GATE_WIDTH / 2.0;
    }

    /**
     * The facing-rotated slab standing on {@code pos} (bottom centre); also the entity's hit box.
     * 立在 {@code pos}（底部中心）上、按朝向旋转的薄板；同时也是实体的命中箱。
     */
    public static Box gateBox(Vec3d pos, Direction facing) {
        double halfX = halfExtentX(facing);
        double halfZ = halfExtentZ(facing);
        return new Box(pos.x - halfX, pos.y, pos.z - halfZ,
                pos.x + halfX, pos.y + RiftwalkerRules.GATE_HEIGHT, pos.z + halfZ);
    }

    /** Centre of the slab (half the height above the position). / 薄板中心（位置上方半个门高）。 */
    public static Vec3d centre(Vec3d pos) {
        return pos.add(0.0, RiftwalkerRules.GATE_HEIGHT / 2.0, 0.0);
    }

    /** The slab shrunk for the emptiness check. / 用于空间检查的收缩薄板。 */
    public static Box clearanceBox(Vec3d pos, Direction facing) {
        return gateBox(pos, facing).contract(CONTACT_EPSILON);
    }

    /** Thin box just under the footprint; something solid must intersect it. / 脚印正下方的薄探测盒，必须与实心物相交。 */
    public static Box supportProbe(Vec3d pos, Direction facing) {
        double halfX = halfExtentX(facing) - CONTACT_EPSILON;
        double halfZ = halfExtentZ(facing) - CONTACT_EPSILON;
        return new Box(pos.x - halfX, pos.y - SUPPORT_PROBE_HEIGHT, pos.z - halfZ,
                pos.x + halfX, pos.y - CONTACT_EPSILON, pos.z + halfZ);
    }

    /**
     * Render-culling box covering model B, used by the entity's {@code getVisibilityBoundingBox}.
     * 覆盖模型 B 的渲染剔除包围盒，供实体的 {@code getVisibilityBoundingBox} 使用。
     */
    public static Box visibilityBox(Vec3d pos) {
        return new Box(pos.x - VISIBILITY_HALF_WIDTH, pos.y - VISIBILITY_BELOW, pos.z - VISIBILITY_HALF_WIDTH,
                pos.x + VISIBILITY_HALF_WIDTH, pos.y + VISIBILITY_ABOVE, pos.z + VISIBILITY_HALF_WIDTH);
    }

    /**
     * C15: the 1 × 2 standing cell directly in front of the gate (its exit side): the gate's width and height,
     * {@link #FRONT_CLEARANCE_DEPTH} deep from the slab's front face. Players leave one block out in front first
     * ({@code RiftExitSearch}) and projectiles always leave just in front ({@code RiftProjectileMath}), so a gate whose
     * front cell holds a block would be a dead exit.
     * C15：门正前方（出口一侧）的 1 × 2 站立格：宽、高与门相同，从薄板正面向前 {@link #FRONT_CLEARANCE_DEPTH} 深。
     * 玩家先从正前方一格出门（{@code RiftExitSearch}），投掷物总是从正前方出来（{@code RiftProjectileMath}），
     * 因此正前方这一格有方块的门就是死门。
     */
    public static Box frontCell(Vec3d pos, Direction facing) {
        Direction front = horizontal(facing);
        double near = RiftwalkerRules.GATE_DEPTH / 2.0;
        double far = near + FRONT_CLEARANCE_DEPTH;
        double half = RiftwalkerRules.GATE_WIDTH / 2.0;
        if (front.getAxis() == Direction.Axis.Z) {
            int sign = front.getOffsetZ();
            return new Box(pos.x - half, pos.y, pos.z + sign * near,
                    pos.x + half, pos.y + RiftwalkerRules.GATE_HEIGHT, pos.z + sign * far);
        }
        int sign = front.getOffsetX();
        return new Box(pos.x + sign * near, pos.y, pos.z - half,
                pos.x + sign * far, pos.y + RiftwalkerRules.GATE_HEIGHT, pos.z + half);
    }

    /** The front cell shrunk for the emptiness check (touching faces never count). / 用于空间检查的收缩正前方格。 */
    public static Box frontClearanceBox(Vec3d pos, Direction facing) {
        return frontCell(pos, facing).contract(CONTACT_EPSILON);
    }

    /** True when {@code inner} lies inside {@code outer} (faces inclusive). / {@code inner} 位于 {@code outer} 内（含边界）。 */
    public static boolean boxWithin(Box outer, Box inner) {
        return inner.minX >= outer.minX && inner.maxX <= outer.maxX
                && inner.minY >= outer.minY && inner.maxY <= outer.maxY
                && inner.minZ >= outer.minZ && inner.maxZ <= outer.maxZ;
    }

    // ---- Floor snap and position / 贴地与位置 ----

    /** Downward floor ray: {start, end}, from just above the feet to {@link #FLOOR_DROP} below. / 向下的地面射线。 */
    public static Vec3d[] floorProbe(Vec3d feet) {
        return new Vec3d[]{feet.add(0.0, FLOOR_PROBE_LIFT, 0.0), feet.subtract(0.0, FLOOR_DROP, 0.0)};
    }

    /**
     * Floor height for the gate base: the top face the floor ray hit (within the probe), otherwise the feet when the
     * player stands on the ground (e.g. on a block edge the centre ray misses); empty when the player is airborne
     * above a longer drop. The service still requires {@link #supportProbe} to touch something solid.
     * 门底的地面高度：地面射线命中的顶面（在探测范围内），否则玩家站在地上时取脚下高度（例如站在方块边缘、中心射线落空）；
     * 玩家悬空且下方落差更大时为空。服务端仍要求 {@link #supportProbe} 与实心物相交。
     */
    public static OptionalDouble snapFloorY(boolean rayHitTopFace, double hitY, boolean onGround, double feetY) {
        if (rayHitTopFace && Double.isFinite(hitY)
                && hitY <= feetY + FLOOR_PROBE_LIFT && hitY >= feetY - FLOOR_DROP) {
            return OptionalDouble.of(hitY);
        }
        return onGround && Double.isFinite(feetY) ? OptionalDouble.of(feetY) : OptionalDouble.empty();
    }

    /**
     * Gate position: the width axis snaps to the centre of the player's block column (a 1-wide corridor always fits
     * and the model lines up with the walls), the facing axis keeps the player's coordinate, y is the floor.
     * 门的位置：宽度轴吸附到玩家所在方块列的中心（1 格宽走廊总能放下，模型与墙对齐），朝向轴保留玩家坐标，y 取地面高度。
     */
    public static Vec3d gatePosition(Vec3d feet, Direction facing, double floorY) {
        if (horizontal(facing).getAxis() == Direction.Axis.Z) {
            return new Vec3d(MathHelper.floor(feet.x) + 0.5, floorY, feet.z);
        }
        return new Vec3d(feet.x, floorY, MathHelper.floor(feet.z) + 0.5);
    }

    // ---- Bounds and spacing / 边界与间距 ----

    /**
     * Centre inside the play area (inclusive), base at least {@link #MIN_BASE_ABOVE_PLAY_AREA} above its floor (a gate
     * lower than that would send people to their death) and top not above its ceiling.
     * 中心位于 play area 内（含边界），门底至少高于其底面 {@link #MIN_BASE_ABOVE_PLAY_AREA}（更低的门会把人送死），
     * 门顶不高于其顶面。
     */
    public static boolean withinPlayArea(Box playArea, Vec3d pos) {
        Vec3d centre = centre(pos);
        return centre.x >= playArea.minX && centre.x <= playArea.maxX
                && centre.z >= playArea.minZ && centre.z <= playArea.maxZ
                && pos.y >= playArea.minY + MIN_BASE_ABOVE_PLAY_AREA
                && pos.y + RiftwalkerRules.GATE_HEIGHT <= playArea.maxY;
    }

    /** The visibility box centre stays below Wathe's moving-train cull line. / 可见包围盒中心低于 Wathe 行驶剔除线。 */
    public static boolean belowCullHeight(Vec3d pos) {
        return visibilityBox(pos).getCenter().y < CULL_CENTRE_Y;
    }

    /**
     * True when {@code centre} is at least {@code RiftwalkerRules.MIN_GATE_SPACING} from every other gate centre.
     * 当 {@code centre} 与其他每扇门的中心距离都不小于 {@code RiftwalkerRules.MIN_GATE_SPACING} 时为 true。
     */
    public static boolean respectsSpacing(Vec3d centre, Iterable<Vec3d> otherCentres) {
        double min = RiftwalkerRules.MIN_GATE_SPACING;
        for (Vec3d other : otherCentres) {
            if (other != null && centre.squaredDistanceTo(other) < min * min) {
                return false;
            }
        }
        return true;
    }

    /** Box that must not intersect any Seeker device box. / 不得与任何搜寻者设备包围盒相交的范围。 */
    public static Box deviceClearanceBox(Vec3d pos, Direction facing) {
        return gateBox(pos, facing).expand(DEVICE_CLEARANCE);
    }

    /**
     * Every block within {@link #NEIGHBOUR_RADIUS} of the gate box (below, beside and above it), checked against
     * {@link RiftGateNeighbourRules#isForbiddenNeighbour}.
     * 门包围盒 {@link #NEIGHBOUR_RADIUS} 格内（下方、侧面与上方）的所有方块，逐一用
     * {@link RiftGateNeighbourRules#isForbiddenNeighbour} 检查。
     */
    public static Iterable<BlockPos> neighbourScan(Vec3d pos, Direction facing) {
        Box area = gateBox(pos, facing).expand(NEIGHBOUR_RADIUS);
        BlockPos min = BlockPos.ofFloored(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.ofFloored(area.maxX - CONTACT_EPSILON, area.maxY - CONTACT_EPSILON,
                area.maxZ - CONTACT_EPSILON);
        return BlockPos.iterate(min, max);
    }
}
