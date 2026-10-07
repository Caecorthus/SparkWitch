package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Pure presentation rules for the placed Rift Gate (plan §5.3, D9, D15 model B): model id and placement maths, the
 * ring/swirl geometry the ambient particles follow, particle budgets, and the instinct-outline viewer predicate. The
 * client classes feed plain facts in; nothing here touches the client, so it is unit-testable.
 * <p>
 * Gate-local frame: {@code side} runs along the gate's width, {@code up} is the height above the gate base (entity
 * position = bottom centre), {@code forward} runs along {@link Direction FACING} (positive = in front, where the lit
 * runes face and where projectiles leave).
 * 已放置裂隙门的纯表现规则（plan §5.3、D9、D15 模型 B）：模型 id 与摆放数学、环境粒子沿用的门环/旋涡几何、粒子预算，
 * 以及本能描边的观察者判定。客户端类只传入事实，这里不接触客户端，便于单元测试。门局部坐标：{@code side} 沿门宽方向，
 * {@code up} 为高出门底的高度（实体位置 = 底部中心），{@code forward} 沿 FACING（正值 = 门前，亮符文与投掷物出口一侧）。
 */
public final class RiftGatePresentationRules {
    /** Placed model B, loaded through Fabric's ModelLoadingPlugin. / 放置模型 B，经 Fabric ModelLoadingPlugin 加载。 */
    public static final Identifier PLACED_MODEL_ID = SparkWitch.id("item/rift_gate_placed");
    /** Model B's ground plane in model units (the floor rune circle floats 0.25 above it). / 模型 B 的地面（模型单位）。 */
    public static final double MODEL_FLOOR_Y = -4.0;
    /**
     * {@code ItemRenderer} centres every model ({@code translate(-0.5, -0.5, -0.5)}), so model y {@code m} lands at
     * {@code m/16 - 0.5}; lifting by {@code 0.5 + 4/16} puts model y −4 on the gate base (not the trap's 0.51).
     * ItemRenderer 会把模型居中（平移 −0.5），模型 y = m 落在 m/16 − 0.5；上移 0.5 + 4/16 使模型 y −4 落在门底（不是捕兽夹的 0.51）。
     */
    public static final double MODEL_LIFT = 0.5 - MODEL_FLOOR_Y / 16.0;

    /** Swirl centre height above the base (model y 14). / 旋涡中心高出门底的高度（模型 y 14）。 */
    public static final double SWIRL_CENTER_UP = modelUp(14.0);
    /** Rune-stone ring: a superellipse through the ten stone centres. / 符文石环：穿过十块石头中心的超椭圆。 */
    public static final double RING_HALF_WIDTH = 6.5 / 16.0;
    public static final double RING_HALF_HEIGHT = 13.25 / 16.0;
    public static final double RING_EXPONENT = 4.0;
    /** Ring stones are 3 units deep around the centre plane. / 符文石在中心平面前后共 3 单位厚。 */
    public static final double RING_HALF_DEPTH = 1.5 / 16.0;
    /** Inner area PORTAL particles are drawn into (inside the 12×32 swirl pill). / PORTAL 粒子被吸入的内区。 */
    public static final double SWIRL_TARGET_HALF_WIDTH = 0.25;
    public static final double SWIRL_TARGET_HALF_HEIGHT = 0.7;

    /** Ambient particles only near the camera (vanilla itself drops them past 32). / 仅在镜头附近生成环境粒子。 */
    public static final double PARTICLE_RANGE = 24.0;
    /**
     * While the local player is inside a gate their camera sits at the gate anchor; that gate's particles would fly
     * into the lens, so gates this close stay quiet for an occupant (others still see them).
     * 本地玩家在门内时镜头位于门锚点；该门的粒子会直冲镜头，因此门内者附近这一范围内的门不生成粒子（其他人照常可见）。
     */
    public static final double OCCUPANT_QUIET_RADIUS = 2.5;
    /**
     * While the local player is inside a gate, the gate whose model encloses the camera is not drawn (the occupant's
     * eye sits inside the frame, ~0.6 from this core): core = base centre + {@code OWN_GATE_CORE_UP}, radius
     * {@code OWN_GATE_HIDE_RADIUS}. Every other gate, and every gate for everyone else, is still drawn.
     * 本地玩家在门内时，不绘制把镜头包在模型里的那扇门（门内者的眼睛位于门框内，距此核心约 0.6）：核心 = 底部中心 +
     * {@code OWN_GATE_CORE_UP}，半径 {@code OWN_GATE_HIDE_RADIUS}。其他门、以及其他所有人看到的门照常绘制。
     */
    public static final double OWN_GATE_CORE_UP = 1.0;
    public static final double OWN_GATE_HIDE_RADIUS = 1.0;
    /** Per-tick spawn chances at the ALL particle setting (~10 portal, ~5 dust per second, a spark every ~2 s). */
    public static final double PORTAL_CHANCE = 0.5;
    public static final double DUST_CHANCE = 0.25;
    public static final double WITCH_CHANCE = 1.0 / 40.0;
    public static final float DUST_SCALE = 0.9F;

    /** Instinct outline and dust share the role colour. / 本能描边与尘粒使用职业色。 */
    public static final int OUTLINE_COLOR = RiftwalkerRules.COLOR;

    private RiftGatePresentationRules() {
    }

    /** Height above the gate base of model y {@code modelY} after the lift. / 抬升后模型 y 对应的离地高度。 */
    public static double modelUp(double modelY) {
        return (modelY - MODEL_FLOOR_Y) / 16.0;
    }

    /**
     * Yaw (degrees, about +Y) that turns the model's front (north, −Z) to {@code facing}: NORTH 0, EAST −90,
     * SOUTH 180, WEST 90. Non-horizontal values fall back to NORTH like {@code RiftGateEntity#setFacing}.
     * 把模型正面（北，−Z）转向 {@code facing} 的偏航角（度，绕 +Y）；非水平方向按 NORTH 处理，与实体 setFacing 一致。
     */
    public static float yawDegrees(Direction facing) {
        Direction horizontal = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        return 180.0F - horizontal.asRotation();
    }

    /** Gate-local point to world space. / 门局部坐标转世界坐标。 */
    public static Vec3d toWorld(double baseX, double baseY, double baseZ, Direction facing,
                                double side, double up, double forward) {
        Direction horizontal = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        double forwardX = horizontal.getOffsetX();
        double forwardZ = horizontal.getOffsetZ();
        // The model's +X, turned with the front: NORTH → +X, EAST → +Z. / 模型 +X 随正面旋转。
        double sideX = -forwardZ;
        double sideZ = forwardX;
        return new Vec3d(baseX + sideX * side + forwardX * forward, baseY + up,
                baseZ + sideZ * side + forwardZ * forward);
    }

    /**
     * Point on the rune-stone ring at parameter {@code t} (radians): {@code {side, up}}.
     * 门环上参数 {@code t}（弧度）处的点：{@code {side, up}}。
     */
    public static double[] ringPoint(double t) {
        double exponent = 2.0 / RING_EXPONENT;
        double cos = Math.cos(t);
        double sin = Math.sin(t);
        double side = RING_HALF_WIDTH * Math.signum(cos) * Math.pow(Math.abs(cos), exponent);
        double up = SWIRL_CENTER_UP + RING_HALF_HEIGHT * Math.signum(sin) * Math.pow(Math.abs(sin), exponent);
        return new double[]{side, up};
    }

    /**
     * Point inside the swirl for unit-disc coordinates {@code (u, v)} (each in −1..1): {@code {side, up}}.
     * 单位圆坐标 {@code (u, v)} 对应的旋涡内部点：{@code {side, up}}。
     */
    public static double[] swirlTarget(double u, double v) {
        return new double[]{SWIRL_TARGET_HALF_WIDTH * u, SWIRL_CENTER_UP + SWIRL_TARGET_HALF_HEIGHT * v};
    }

    /**
     * Spawn-chance multiplier for the client's particle setting: ALL 1, DECREASED ½, MINIMAL 0 (nothing).
     * 客户端粒子设置对应的生成概率系数：全部 1、减少 ½、最少 0（不生成）。
     */
    public static double particleChanceScale(ParticlesMode mode) {
        if (mode == null) {
            return 0.0;
        }
        return switch (mode) {
            case ALL -> 1.0;
            case DECREASED -> 0.5;
            case MINIMAL -> 0.0;
        };
    }

    /** Whether a gate this far (squared, to the swirl centre) from the camera emits particles. / 是否在粒子范围内。 */
    public static boolean inParticleRange(double cameraDistanceSquared) {
        return cameraDistanceSquared <= PARTICLE_RANGE * PARTICLE_RANGE;
    }

    /** An occupant's own (nearby) gate stays quiet. / 门内者身边的门不生成粒子。 */
    public static boolean quietForOccupant(boolean viewerInsideGate, double cameraDistanceSquared) {
        return viewerInsideGate && cameraDistanceSquared < OCCUPANT_QUIET_RADIUS * OCCUPANT_QUIET_RADIUS;
    }

    /**
     * Whether the renderer skips this gate: only for a viewer inside a gate whose camera is within
     * {@link #OWN_GATE_HIDE_RADIUS} of the gate core (squared distance in).
     * 渲染器是否跳过这扇门：仅当观察者在门内、且镜头距门核心不超过 {@link #OWN_GATE_HIDE_RADIUS} 时（传入距离平方）。
     */
    public static boolean hidesOwnGate(boolean viewerInsideGate, double cameraToCoreDistanceSquared) {
        return viewerInsideGate && cameraToCoreDistanceSquared <= OWN_GATE_HIDE_RADIUS * OWN_GATE_HIDE_RADIUS;
    }

    /** Role colour as dust RGB (0..1). / 职业色的尘粒 RGB（0..1）。 */
    public static Vector3f dustColor() {
        return new Vector3f((OUTLINE_COLOR >> 16 & 0xFF) / 255.0F, (OUTLINE_COLOR >> 8 & 0xFF) / 255.0F,
                (OUTLINE_COLOR & 0xFF) / 255.0F);
    }

    /**
     * D9: only a live, playing witch-faction viewer (Grand Witch, plain Accomplice or any special accomplice, read
     * from the viewer's RAW role) gets the key-gated gate outline through walls; everyone else falls through to
     * Wathe's default (no gate outline). A gate occupant is still alive, so it keeps the outline inside.
     * D9：只有存活且在局中的魔女阵营观察者（大魔女、普通共犯或任一特殊共犯，读取观察者的真实职业）按本能键时能隔墙看到门；
     * 其他人交给 Wathe 默认逻辑（不描边门）。门内者仍算存活，所以在门内也保留描边。
     */
    public static boolean outlinesGate(boolean confirmedServer, boolean viewerPlayingAndAlive,
                                       boolean viewerGrandWitch, boolean viewerAccompliceLike) {
        return confirmedServer && viewerPlayingAndAlive && (viewerGrandWitch || viewerAccompliceLike);
    }
}
