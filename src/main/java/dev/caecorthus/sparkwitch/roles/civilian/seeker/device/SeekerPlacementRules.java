package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.doctor4t.wathe.block.CrosshairEnabling;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block.MountableBlock;
import dev.doctor4t.wathe.block.VentHatchBlock;
import dev.doctor4t.wathe.index.tag.WatheBlockTags;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * WP-03 internal placement rules for the car spawn point and the camera mount (reach, support, play area, forbidden
 * neighbours). Everything except {@link #isForbiddenNeighbour(BlockState)} is pure geometry and side-neutral; the
 * server ({@code SeekerDeviceService}) is the only caller that decides a placement.
 * WP-03 内部的放置规则：小车生成点与摄像头安装位置（距离、支撑、游戏区域、禁放邻居）。除
 * {@link #isForbiddenNeighbour(BlockState)} 外均为纯几何且两端通用；只有服务端（{@code SeekerDeviceService}）据此决定放置。
 */
public final class SeekerPlacementRules {
    /** Collision checks shrink boxes by this much so faces that merely touch never count. / 碰撞检查时收缩的余量。 */
    public static final double CONTACT_EPSILON = 1.0E-4;
    /** Height of the probe under the car footprint that must hit something solid. / 小车脚下支撑探测盒的高度。 */
    public static final double SUPPORT_PROBE_HEIGHT = 0.1;
    /** Idle car steps this far away from an overlapping corpse. / 空闲小车离开重叠尸体的步长。 */
    public static final double BODY_PUSH_DISTANCE = 0.5;
    /** Ticks between idle corpse push-off checks. / 空闲推离尸体的检查间隔。 */
    public static final int BODY_PUSH_INTERVAL_TICKS = 5;
    /** Ticks between camera mount checks. / 摄像头依附面的检查间隔。 */
    public static final int CAMERA_SUPPORT_CHECK_INTERVAL_TICKS = 10;
    /** The camera sight probe runs this far past the reported hit. / 摄像头视线探测越过命中点的距离。 */
    public static final double SIGHT_PROBE_OVERSHOOT = 0.05;
    private static final double HORIZONTAL_EPSILON = 1.0E-3;

    private SeekerPlacementRules() {
    }

    // ---- Car ----

    /** Horizontal unit look vector for a Minecraft yaw (0 = +Z). / 由 Minecraft 偏航得到的水平单位视线向量。 */
    public static Vec3d horizontalLook(float yaw) {
        float radians = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(radians), 0.0, MathHelper.cos(radians));
    }

    /**
     * A block-ray hit is used directly only when it lands on a top face within {@link SeekerRules#DEPLOY_REACH}.
     * 方块射线命中点仅当落在顶面且在部署距离内时直接使用。
     */
    public static boolean isDirectCarSpot(Direction side, Vec3d eye, Vec3d hit) {
        return side == Direction.UP && withinReach(eye, hit, SeekerRules.DEPLOY_REACH);
    }

    /** Fallback origin: the feet moved {@link SeekerRules#DEPLOY_FORWARD} along the horizontal look. / 兜底起点。 */
    public static Vec3d fallbackCarOrigin(Vec3d feet, float yaw) {
        return feet.add(horizontalLook(yaw).multiply(SeekerRules.DEPLOY_FORWARD));
    }

    /**
     * Downward search segment for the fallback: from half a block above the origin to {@link SeekerRules#DEPLOY_DROP}
     * below it. Returns {start, end}.
     * 兜底落点的向下搜索线段：从起点上方半格到下方 {@link SeekerRules#DEPLOY_DROP} 格。返回 {起点, 终点}。
     */
    public static Vec3d[] fallbackDropSegment(Vec3d origin) {
        return new Vec3d[]{origin.add(0.0, 0.5, 0.0), origin.subtract(0.0, SeekerRules.DEPLOY_DROP, 0.0)};
    }

    /** Traversal box (0.4 x 0.6) shrunk for emptiness checks. / 用于空间检查的收缩通行箱。 */
    public static Box carClearanceBox(Vec3d feet) {
        return SeekerCarPhysics.traversalBox(feet).contract(CONTACT_EPSILON);
    }

    /** Thin box just under the car footprint; something solid must intersect it. / 小车脚下的薄探测盒。 */
    public static Box carSupportProbe(Vec3d feet) {
        double half = SeekerRules.CAR_TRAVERSAL_WIDTH / 2.0 - CONTACT_EPSILON;
        return new Box(feet.x - half, feet.y - SUPPORT_PROBE_HEIGHT, feet.z - half,
                feet.x + half, feet.y - CONTACT_EPSILON, feet.z + half);
    }

    /** Inclusive on every face so a spot on the play-area floor still counts. / 各面均包含边界。 */
    public static boolean withinPlayArea(Box playArea, Vec3d position) {
        return position.x >= playArea.minX && position.x <= playArea.maxX
                && position.y >= playArea.minY && position.y <= playArea.maxY
                && position.z >= playArea.minZ && position.z <= playArea.maxZ;
    }

    /** VOID rule: the car fell out of the train. / VOID 规则：小车掉出了列车。 */
    public static boolean isBelowPlayArea(double y, Box playArea) {
        return y < playArea.minY;
    }

    public static boolean withinReach(Vec3d eye, Vec3d target, double reach) {
        return reach > 0.0 && eye.squaredDistanceTo(target) <= reach * reach;
    }

    /**
     * Horizontal unit direction pushing the car away from a corpse; when both centres coincide the car backs off
     * against its own heading.
     * 把小车推离尸体的水平单位方向；两者中心重合时小车沿自身朝向后退。
     */
    public static Vec3d pushOffDirection(Vec3d car, Vec3d body, float carYaw) {
        Vec3d away = new Vec3d(car.x - body.x, 0.0, car.z - body.z);
        if (away.lengthSquared() < HORIZONTAL_EPSILON * HORIZONTAL_EPSILON) {
            return horizontalLook(carYaw).negate();
        }
        return away.normalize();
    }

    // ---- Camera ----

    /**
     * Entity position (bottom centre of the 0.3 cube) for a camera mounted on {@code face} of {@code support} at
     * {@code hit}. The cube touches the face plane and stays inside the face's footprint.
     * 摄像头实体位置（0.3 立方体的底面中心）：立方体贴合所点击面的平面，并保持在该面的范围之内。
     */
    public static Vec3d cameraEntityPos(Vec3d hit, BlockPos support, Direction face) {
        double size = SeekerRules.CAMERA_SIZE;
        double half = size / 2.0;
        double x = clampToFace(hit.x, support.getX(), half);
        double y = clampToFace(hit.y, support.getY(), half);
        double z = clampToFace(hit.z, support.getZ(), half);
        return switch (face) {
            case UP -> new Vec3d(x, support.getY() + 1.0, z);
            case DOWN -> new Vec3d(x, support.getY() - size, z);
            case NORTH -> new Vec3d(x, y - half, support.getZ() - half);
            case SOUTH -> new Vec3d(x, y - half, support.getZ() + 1.0 + half);
            case WEST -> new Vec3d(support.getX() - half, y - half, z);
            case EAST -> new Vec3d(support.getX() + 1.0 + half, y - half, z);
        };
    }

    /** The 0.3 cube of a camera at {@code position}. / 摄像头实体的 0.3 立方体。 */
    public static Box cameraBox(Vec3d position) {
        double half = SeekerRules.CAMERA_SIZE / 2.0;
        return new Box(position.x - half, position.y, position.z - half,
                position.x + half, position.y + SeekerRules.CAMERA_SIZE, position.z + half);
    }

    /**
     * Horizontal centre of the view: wall cameras look along the face normal; floor/ceiling cameras use the placing
     * player's yaw.
     * 视野的水平中心：墙面摄像头沿面法线；地面与天花板摄像头取放置者的偏航。
     */
    public static float cameraMountYaw(Direction face, float playerYaw) {
        if (face.getAxis() == Direction.Axis.Y) {
            return MathHelper.wrapDegrees(playerYaw);
        }
        return face.asRotation();
    }

    /** The block a mounted camera hangs on. / 摄像头所依附的方块。 */
    public static BlockPos cameraSupportPos(Vec3d position, Direction facing) {
        double half = SeekerRules.CAMERA_SIZE / 2.0;
        Vec3d centre = position.add(0.0, half, 0.0);
        Vec3d inward = Vec3d.of(facing.getVector()).multiply(-(half + 0.05));
        return BlockPos.ofFloored(centre.add(inward));
    }

    /**
     * Anti-cheat for the client-reported block hit: the eye must be on the outward side of the clicked face plane.
     * 针对客户端上报的方块命中的反作弊：眼睛必须位于所点击面平面的外侧。
     */
    public static boolean isOutwardOf(Vec3d eye, BlockPos support, Direction face) {
        Direction.Axis axis = face.getAxis();
        double plane = support.getComponentAlongAxis(axis)
                + (face.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
        double offset = eye.getComponentAlongAxis(axis) - plane;
        return face.getDirection() == Direction.AxisDirection.POSITIVE ? offset > 0.0 : offset < 0.0;
    }

    /**
     * End of the server line-of-sight probe that replays the client crosshair: the eye-to-hit ray extended by
     * {@link #SIGHT_PROBE_OVERSHOOT} so float rounding still reaches the surface. The first block the probe hits must
     * be the clicked support on the clicked face; a modified client can therefore not claim the far side of a wall.
     * 复现客户端准星的服务端视线探测终点：眼睛到命中点的射线再延长一小段以抵消浮点误差。探测命中的第一个方块必须
     * 是被点击方块的被点击面，因此改过的客户端无法声称点中了墙的另一侧。
     */
    public static Vec3d sightProbeEnd(Vec3d eye, Vec3d hit) {
        return hit.add(hit.subtract(eye).normalize().multiply(SIGHT_PROBE_OVERSHOOT));
    }

    /** Scan area for forbidden neighbours around the camera's own block. / 禁放邻居的扫描范围。 */
    public static Iterable<BlockPos> forbiddenScan(BlockPos cameraBlock) {
        int radius = SeekerRules.CAMERA_FORBIDDEN_RADIUS;
        return BlockPos.iterate(cameraBlock.add(-radius, -radius, -radius), cameraBlock.add(radius, radius, radius));
    }

    /**
     * Doors (Wathe door parts, vanilla doors, trapdoors, gates), vent hatches, beds, seats (every Wathe mountable
     * block) and other interactive blocks (buttons, levers, Wathe crosshair blocks such as cabinets and cargo boxes,
     * food platters and drink trays). A camera may not be mounted within one block of any of them.
     * 门（Wathe 门、原版门、活板门、栅栏门）、通风口舱盖、床、座位（所有 Wathe 可乘坐方块）及其他可交互方块
     * （按钮、拉杆、柜子与货箱等 Wathe 准星方块、餐盘与饮料托盘）。摄像头不得安装在它们 1 格之内。
     */
    public static boolean isForbiddenNeighbour(@Nullable BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof DoorPartBlock
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock
                || block instanceof VentHatchBlock
                || block instanceof BedBlock
                || block instanceof MountableBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof CrosshairEnabling
                || block instanceof FoodPlatterBlock
                || state.isIn(BlockTags.DOORS)
                || state.isIn(BlockTags.TRAPDOORS)
                || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.BEDS)
                || state.isIn(BlockTags.BUTTONS)
                || state.isIn(WatheBlockTags.VENT_HATCHES);
    }

    private static double clampToFace(double value, int blockMin, double half) {
        return MathHelper.clamp(value, blockMin + half, blockMin + 1.0 - half);
    }
}
