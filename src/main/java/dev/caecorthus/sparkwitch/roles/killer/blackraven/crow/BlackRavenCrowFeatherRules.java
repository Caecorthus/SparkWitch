package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Pure rules of the Homing Feather (归羽, plan §0 and Q7): one drop per Raven Form session, a marker that drifts down
 * like a feather and comes to rest, visible to everyone, never picked up, pushed or destroyed, and alive only while the
 * session that dropped it still names it. Shared by the server marker, its client simulation and the tests.
 * 归羽（计划 §0 与 Q7）的纯规则：每次化鸦会话只能丢出一次；标记像羽毛一样缓缓飘落并静止，所有人可见，不能拾取、推动或破坏，
 * 且只在丢出它的会话仍指向它时存在。供服务端标记、其客户端模拟与测试共用。
 */
public final class BlackRavenCrowFeatherRules {
    /** Item and marker entity id {@code sparkwitch:homing_feather}. / 物品与标记实体 id。 */
    public static final Identifier FEATHER_ID = BlackRavenCrowRules.HOMING_FEATHER_ID;
    /** Particle type id {@code sparkwitch:black_feather}. / 粒子类型 id。 */
    public static final Identifier PARTICLE_ID = BlackRavenCrowRules.BLACK_FEATHER_PARTICLE_ID;

    /** Marker hitbox: a small feather lying on the floor. / 标记命中箱：落在地上的一根小羽毛。 */
    public static final float WIDTH = 0.25F;
    public static final float HEIGHT = 0.125F;
    /** Tracked like a player-visible object: 8 chunks, every 2 ticks. / 按玩家可见物体追踪：8 区块，每 2 刻。 */
    public static final int TRACKING_RANGE = 8;
    public static final int TRACKING_INTERVAL = 2;
    /** Every client keeps drawing it up to 64 blocks (owner Q7: visible to everyone). / 所有客户端在 64 格内绘制（Q7）。 */
    public static final double RENDER_DISTANCE_SQUARED = 64.0 * 64.0;

    /** Feather fall: slow gravity, a low terminal speed and strong air drag. / 羽毛下落：弱重力、低终端速度与强空气阻力。 */
    public static final double GRAVITY = 0.02;
    public static final double TERMINAL_FALL = 0.2;
    public static final double AIR_DRAG = 0.91;
    public static final double GROUND_DRAG = 0.5;
    /** Below this squared speed a grounded feather is at rest and only re-checks every few ticks. / 静止阈值。 */
    public static final double REST_SPEED_SQUARED = 1.0E-5;
    public static final int REST_RECHECK_TICKS = 4;
    /** Reported position changes smaller than this are not synced to the owner. / 小于此值的位置变化不同步给主人。 */
    public static final double REPORT_EPSILON_SQUARED = 1.0E-4;
    /**
     * A still-falling feather is projected straight down to the floor this far at most when the session ends.
     * 会话结束时仍在下落的归羽，最多向正下方投射这么远找到地面。
     */
    public static final double MAX_PROJECTED_DROP = 48.0;

    /**
     * Chunk ticket around a dropped feather (radius 2: its own chunk entity-ticking, the ring around it loaded), so a
     * feather the crow left far behind still falls and its landing area stays checkable.
     * 已丢出归羽周围的区块票据（半径 2：所在区块处理实体，周围一圈保持加载），使乌鸦飞远后归羽仍能下落、落点仍可检查。
     */
    public static final int TICKET_RADIUS = 2;
    public static final int TICKET_EXPIRY_TICKS = 40;

    private BlackRavenCrowFeatherRules() {
    }

    /** One drop per session: only while flying and only before a feather is out. / 每次会话一次：仅飞行中且尚未丢出时。 */
    public static boolean canDrop(boolean inSession, boolean featherAlreadyOut) {
        return inSession && !featherAlreadyOut;
    }

    /**
     * Server self-check of a marker: it lives only while its owner is online, still flies, and the session still
     * records exactly this marker. Anything else (round end, death, disconnect, role loss, a newer session) removes it.
     * 标记的服务端自检：只有主人在线、仍在飞行、且会话记录的正是该标记时才存在；其他情况（回合结束、死亡、断线、失去职业、新会话）一律移除。
     */
    public static boolean keepsMarker(boolean ownerOnline, boolean sessionActive, int recordedEntityId,
                                      int markerEntityId) {
        return ownerOnline && sessionActive && recordedEntityId == markerEntityId;
    }

    /**
     * Velocity before this tick's move: gravity capped at the terminal fall, horizontal drift kept.
     * 本刻移动前的速度：施加重力并限制终端下落速度，保留水平漂移。
     */
    public static Vec3d preMoveVelocity(Vec3d velocity, boolean noGravity) {
        if (noGravity) {
            return velocity;
        }
        return new Vec3d(velocity.x, Math.max(velocity.y - GRAVITY, -TERMINAL_FALL), velocity.z);
    }

    /** Velocity after this tick's move: air or ground drag; grounded feathers stop falling. / 移动后的速度：空气或地面阻力。 */
    public static Vec3d postMoveVelocity(Vec3d velocity, boolean onGround) {
        if (onGround) {
            return new Vec3d(velocity.x * GROUND_DRAG, 0.0, velocity.z * GROUND_DRAG);
        }
        return new Vec3d(velocity.x * AIR_DRAG, velocity.y, velocity.z * AIR_DRAG);
    }

    /** Whether the marker moves this tick (a resting feather re-checks every few ticks). / 本刻是否移动。 */
    public static boolean movesThisTick(boolean onGround, double speedSquared, int age) {
        return !onGround || speedSquared > REST_SPEED_SQUARED || age % REST_RECHECK_TICKS == 0;
    }

    /** Whether a moved marker reports its new position to the owner's component. / 位置变化是否需要同步。 */
    public static boolean reportsMove(Vec3d reported, Vec3d current) {
        return reported.squaredDistanceTo(current) > REPORT_EPSILON_SQUARED;
    }
}
