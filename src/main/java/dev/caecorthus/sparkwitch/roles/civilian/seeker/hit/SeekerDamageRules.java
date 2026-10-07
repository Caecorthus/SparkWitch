package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Frozen contract: who may break a Seeker device, plus the pure blast / line-of-sight / aim helpers every hit entry
 * validates with. The gate deliberately mirrors the Control Expert's {@code canAffect} chain with the device OWNER as
 * the proxy target, because SparkFactionAPI never vetoes non-player targets and the Vendetta packet guard ignores
 * them: the attacker must be a role-agnostic round participant ({@link SeekerTargeting#isRoundParticipant}: alive, not
 * spectator/creative, not an active Wraith, any role; never the Seeker-only {@code isActiveParticipant}) other than
 * the owner, not in a Seeker session, not stunned, not
 * Last-Escape blocked, allowed by {@code SparkFactionApi.canAffectPlayer(attacker, owner, BREAK_ACTION_ID, game)}, and
 * inside Vendetta isolation. Police and teammates may break devices ("other players"). A device whose owner is
 * offline has no proxy target and fails closed. A device the attacker may not break is transparent to that attacker's
 * server-side rays and sweeps (shotgun, taser, feather blade, death ray, throwing axe, shuriken, Shock Device): it
 * neither breaks nor shields the player behind it. Client-targeted weapons (revolver, derringer, Demon Hunter pistol,
 * knife stab) are the documented exception: the client cannot evaluate the faction veto, Vendetta isolation or
 * Last Escape, so it names the nearer device in the payload, and when the server refuses the break the shot or stab
 * resolves as a miss (the device shields the player behind it). The server does not re-resolve a player target,
 * because it cannot know the per-gun client range (derringer 7 blocks, SparkTraits Marksman extension).
 * 冻结契约：谁可以损坏搜寻者设备，以及各命中入口用于校验的纯爆炸/视线/瞄准辅助方法。该门槛刻意复制控场专家
 * {@code canAffect} 判定链，并以设备拥有者作为代理目标，因为 SparkFactionAPI 从不否决非玩家目标、复仇者数据包拦截也会忽略
 * 它们：攻击者必须是拥有者以外、与职业无关的对局参与者（{@link SeekerTargeting#isRoundParticipant}：存活、非旁观/创造、
 * 非激活冤魂、任意职业；绝不是仅限搜寻者的 {@code isActiveParticipant}），不在搜寻者会话中、未被眩晕、未被最后逃脱阻止，通过
 * {@code SparkFactionApi.canAffectPlayer(attacker, owner, BREAK_ACTION_ID, game)}，并满足复仇者隔离。警察与队友也能
 * 打坏设备（规格写的是“其他玩家”）。拥有者离线的设备没有代理目标，失败即关闭。攻击者不能打坏的设备对其服务端射线与
 * 扫掠（猎枪、电击枪、羽刃、死光、飞斧、手里剑、电击装置）透明：既不会损坏，也不会替身后的玩家挡枪。由客户端选择目标的
 * 武器（左轮、德林加、猎魔枪、刀刺）是有文档说明的例外：客户端无法判断阵营否决、复仇者隔离与最后逃脱，会在数据包中指定
 * 更近的设备；服务端拒绝损坏时这一枪/一刺按未命中处理（设备替身后的玩家挡下）。服务端不会重新解析玩家目标，
 * 因为它无法得知各枪械的客户端射程（德林加 7 格、SparkTraits 神射手延长）。
 */
public final class SeekerDamageRules {
    /** Wathe's server cap for gun targets ({@code GunShootPayload$Receiver}). / Wathe 服务端枪械目标距离上限。 */
    public static final double GUN_MAX_DISTANCE = 65.0;
    /**
     * Latency fallback aim tolerance between the shooter's look vector and any device sample point; the primary gun
     * rule is the look ray meeting the margin-grown box ({@link #aimedAndVisible}).
     * 射手视线与任一设备采样点的夹角容差，作为延迟兜底；枪械的主规则是视线射线与扩大余量后的箱体相交。
     */
    public static final double GUN_MAX_ANGLE_DEGREES = 25.0;
    /** Wathe knife stab reach plus a latency tolerance. / Wathe 刀刺距离加延迟容差。 */
    public static final double KNIFE_REACH = 3.0;
    public static final double KNIFE_REACH_TOLERANCE = 0.5;
    /** Wathe grenade blast (sphere; Wathe's own cube is not reused). / Wathe 手雷爆炸半径（球形，不沿用 Wathe 的立方体）。 */
    public static final double GRENADE_RADIUS = 3.0;
    /** SparkStrength M67 blast radius ({@code M67Rules.BLAST_RADIUS}). / SparkStrength M67 爆炸半径。 */
    public static final double M67_RADIUS = 3.5;
    /** Sample points sit this far inside the device box so they never touch a block face. / 采样点内缩量，避免贴合方块表面。 */
    static final double SAMPLE_INSET = 0.01;
    private static final double LOS_EPSILON = 1.0E-4;

    private SeekerDamageRules() {
    }

    /**
     * Server only. See the class Javadoc; later seams are consulted only after every earlier one passed.
     * 仅服务端。见类说明；只有前序条件全部通过后才查询后续接缝。
     */
    public static boolean mayBreak(@Nullable ServerPlayerEntity attacker, @Nullable ServerPlayerEntity owner,
                                   GameWorldComponent game) {
        if (attacker == null || owner == null || game == null) {
            return false;
        }
        return breaks(
                game.isRunning(),
                isSamePlayer(attacker, owner),
                () -> SeekerTargeting.isRoundParticipant(attacker),
                () -> isInSeekerSession(attacker),
                () -> SeekerControlExpertBridge.isStunned(attacker),
                () -> SparkTraitsKillerBridge.isKillerInteractionBlocked(attacker),
                () -> SparkFactionApi.canAffectPlayer(attacker, owner, SeekerRules.BREAK_ACTION_ID, game),
                () -> vendettaAllows(attacker, owner));
    }

    /** Pure gate in evaluation order. / 按求值顺序排列的纯判定。 */
    static boolean breaks(boolean running, boolean samePlayer, BooleanSupplier participant, BooleanSupplier inSession,
                          BooleanSupplier stunned, BooleanSupplier lastEscapeBlocked, BooleanSupplier factionAllows,
                          BooleanSupplier vendettaAllows) {
        return running
                && !samePlayer
                && participant.getAsBoolean()
                && !inSession.getAsBoolean()
                && !stunned.getAsBoolean()
                && !lastEscapeBlocked.getAsBoolean()
                && factionAllows.getAsBoolean()
                && vendettaAllows.getAsBoolean();
    }

    /**
     * Mirrors the Vendetta packet guard with the owner as proxy: an active Vendetta endpoint reaches only its exact
     * pair. / 以拥有者为代理复制复仇者数据包拦截：激活的复仇者一端只能影响其精确配对。
     */
    static boolean vendettaAllows(PlayerEntity attacker, PlayerEntity owner) {
        return vendettaAllows(VendettaInteractionService.isActiveVendetta(attacker),
                VendettaInteractionService.isActiveVendetta(owner),
                VendettaInteractionService.isExactPair(attacker, owner));
    }

    static boolean vendettaAllows(boolean attackerVendetta, boolean ownerVendetta, boolean exactPair) {
        return !(attackerVendetta || ownerVendetta) || exactPair;
    }

    /** Reads the owner-synced Seeker component; any non-NONE mode is "in session". / 读取搜寻者组件；非 NONE 即会话中。 */
    static boolean isInSeekerSession(PlayerEntity player) {
        return SeekerStatusComponent.KEY.maybeGet(player)
                .map(component -> component.sessionMode() != SeekerSessionMode.NONE)
                .orElse(false);
    }

    static boolean isSamePlayer(PlayerEntity first, PlayerEntity second) {
        return first == second || first.getUuid().equals(second.getUuid());
    }

    // ---- Pure geometry ----

    /** Blast sphere on centres (closed). / 以中心计算的闭球判定。 */
    public static boolean inBlastSphere(Vec3d center, Vec3d deviceCenter, double radius) {
        return Double.isFinite(radius) && radius >= 0.0 && isFinite(center) && isFinite(deviceCenter)
                && center.squaredDistanceTo(deviceCenter) <= radius * radius;
    }

    /** Angle between {@code look} and {@code toTarget} is at most {@code maxDegrees}. / 两向量夹角不超过给定角度。 */
    public static boolean withinAimCone(Vec3d look, Vec3d toTarget, double maxDegrees) {
        double lengths = look.length() * toTarget.length();
        if (!(lengths > 0.0) || !Double.isFinite(lengths)) {
            return toTarget.lengthSquared() == 0.0;
        }
        double cosine = MathHelper.clamp(look.dotProduct(toTarget) / lengths, -1.0, 1.0);
        return cosine >= Math.cos(Math.toRadians(maxDegrees)) - 1.0E-9;
    }

    /** Squared distance from {@code point} to the closest point of {@code box} (0 inside). / 点到箱体最近点的平方距离。 */
    public static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Sample points of a device box, all inset by {@link #SAMPLE_INSET} so they stay strictly inside the device (and
     * therefore strictly outside the block a wall camera hangs on): centre, the 6 face centres and the 8 corners (15).
     * The first four are the original set (centre, top centre, two opposite top corners) in their original order, so
     * the richer set only ever adds visible or aimed points and never weakens a result (the car included). Any clearly
     * visible face of a small wall camera now counts, even when the rays to its centre cross its own mounting block.
     * 设备箱体的采样点，全部内缩 {@link #SAMPLE_INSET}，保证严格位于设备内部（因而严格位于墙面摄像头依附方块之外）：
     * 中心、6 个面中心与 8 个角（共 15 个）。前四个即原采样集（中心、顶面中心、两个对角顶角）且顺序不变，
     * 因此更丰富的采样只会增加可见/可瞄准的点，绝不削弱原有结果（小车同样如此）。即使到小型墙面摄像头中心的射线
     * 穿过其依附方块，只要有一个面清晰可见即可。
     */
    public static List<Vec3d> samplePoints(Box box) {
        Box in = insetBox(box);
        Vec3d c = box.getCenter();
        return List.of(
                c,
                new Vec3d(c.x, in.maxY, c.z),
                new Vec3d(in.minX, in.maxY, in.minZ),
                new Vec3d(in.maxX, in.maxY, in.maxZ),
                new Vec3d(c.x, in.minY, c.z),
                new Vec3d(in.minX, c.y, c.z),
                new Vec3d(in.maxX, c.y, c.z),
                new Vec3d(c.x, c.y, in.minZ),
                new Vec3d(c.x, c.y, in.maxZ),
                new Vec3d(in.maxX, in.maxY, in.minZ),
                new Vec3d(in.minX, in.maxY, in.maxZ),
                new Vec3d(in.minX, in.minY, in.minZ),
                new Vec3d(in.maxX, in.minY, in.minZ),
                new Vec3d(in.minX, in.minY, in.maxZ),
                new Vec3d(in.maxX, in.minY, in.maxZ));
    }

    /** Whether any sample point lies within {@code maxDegrees} of the look vector. / 是否有采样点落在瞄准锥内。 */
    public static boolean aimedAt(Vec3d eye, Vec3d look, Box box, double maxDegrees) {
        for (Vec3d point : samplePoints(box)) {
            if (withinAimCone(look, point.subtract(eye), maxDegrees)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pure line of sight: true on the first sample point whose segment from {@code from} {@code clear} accepts (early
     * exit, at most 15 segment tests). / 纯视线判定：第一个被 {@code clear} 接受的采样点即返回真（提前退出，最多 15 次）。
     */
    static boolean anySampleVisible(Vec3d from, Box box, BiPredicate<Vec3d, Vec3d> clear) {
        for (Vec3d point : samplePoints(box)) {
            if (clear.test(from, point)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The point the gun validation tests line of sight to: where the look ray {@code eye → end} enters the real device
     * {@code box}; when it only passes through the targeting margin ({@code targetBox}, the same grown box the client
     * pick uses), that entry clamped into the inset real box, so the tested point is always on or inside the device and
     * never inside the block a wall camera hangs on. Empty when the ray misses {@code targetBox}.
     * 枪械校验测试视线的目标点：视线射线 {@code eye → end} 进入真实设备箱体处；若射线只穿过瞄准余量（{@code targetBox}，
     * 与客户端选择使用的扩大箱体相同），则取该入射点夹紧到内缩后的真实箱体，因此被测点始终位于设备表面或内部，
     * 绝不会落入墙面摄像头依附的方块。射线未与 {@code targetBox} 相交时为空。
     */
    static Optional<Vec3d> rayAimPoint(Vec3d eye, Vec3d end, Box box, Box targetBox) {
        Optional<Vec3d> direct = SeekerDeviceRaycast.entryPoint(eye, end, box);
        if (direct.isPresent()) {
            return direct;
        }
        Box in = insetBox(box);
        return SeekerDeviceRaycast.entryPoint(eye, end, targetBox).map(point -> new Vec3d(
                MathHelper.clamp(point.x, in.minX, in.maxX),
                MathHelper.clamp(point.y, in.minY, in.maxY),
                MathHelper.clamp(point.z, in.minZ, in.maxZ)));
    }

    /**
     * Stable gun validation contract for a client-picked device (Wathe revolver/derringer, Demon Hunter pistol).
     * Accepts when (1) the look ray {@code eye → eye + look × maxDistance} meets the margin-grown {@code targetBox} and the
     * segment to {@link #rayAimPoint} is clear (the client's own pick rule, so a hit that visibly lands is never
     * refused), or (2) the latency fallback: some sample point is within {@code maxDegrees} of the look and some sample
     * point is visible. Both paths need a clear segment to a point of the device itself, so nothing breaks through a
     * wall. The distance cap and {@link #mayBreak} stay with the caller.
     * 客户端选中设备时的稳定枪械校验契约（Wathe 左轮/德林加、猎魔枪）。满足以下任一条件即接受：（1）视线射线
     * {@code eye → eye + look × maxDistance} 与扩大余量后的 {@code targetBox} 相交，且到 {@link #rayAimPoint} 的线段无遮挡
     * （即客户端自身的选择规则，确保看得见的命中不会被拒绝）；（2）延迟兜底：有采样点位于 {@code maxDegrees} 瞄准锥内，
     * 且有采样点可见。两条路径都要求到设备自身某点的线段无遮挡，因此不会隔墙打坏。距离上限与 {@link #mayBreak} 仍由调用方负责。
     */
    static boolean aimedAndVisible(Vec3d eye, Vec3d look, double maxDistance, Box box, Box targetBox,
                                   double maxDegrees, BiPredicate<Vec3d, Vec3d> clear) {
        if (isFinite(look) && look.lengthSquared() > 0.0 && maxDistance > 0.0 && Double.isFinite(maxDistance)) {
            Optional<Vec3d> point = rayAimPoint(eye, eye.add(look.normalize().multiply(maxDistance)), box, targetBox);
            if (point.isPresent() && clear.test(eye, point.get())) {
                return true;
            }
        }
        return aimedAt(eye, look, box, maxDegrees) && anySampleVisible(eye, box, clear);
    }

    /** {@code box} shrunk by the sample inset (capped at half its thinnest side). / 按采样内缩量收缩后的箱体。 */
    private static Box insetBox(Box box) {
        double inset = Math.min(SAMPLE_INSET, Math.min(box.getLengthX(), Math.min(box.getLengthY(), box.getLengthZ())) / 2.0);
        return box.expand(-inset);
    }

    // ---- World line of sight (server) ----

    /**
     * True when at least one sample point of {@code box} is visible from {@code from} through COLLIDER shapes.
     * {@code context} may be null (absent shape context, e.g. a blast centre).
     * 当 {@code box} 至少有一个采样点能从 {@code from} 经 COLLIDER 形状看到时为真；{@code context} 可为空（如爆心）。
     */
    public static boolean hasLineOfSight(World world, Vec3d from, Box box, @Nullable Entity context) {
        return anySampleVisible(from, box, (start, end) -> segmentClear(world, start, end, context));
    }

    /**
     * Server gun validation in {@code world} ({@link #aimedAndVisible} with COLLIDER segments and the gun cone).
     * 服务端枪械校验（以 COLLIDER 线段与枪械瞄准锥调用 {@link #aimedAndVisible}）。
     */
    public static boolean gunAimedAndVisible(World world, Vec3d eye, Vec3d look, double maxDistance, Box box,
                                             Box targetBox, @Nullable Entity context) {
        return aimedAndVisible(eye, look, maxDistance, box, targetBox, GUN_MAX_ANGLE_DEGREES,
                (start, end) -> segmentClear(world, start, end, context));
    }

    /** No COLLIDER block between {@code from} and {@code to}. / 两点之间没有 COLLIDER 方块。 */
    public static boolean segmentClear(World world, Vec3d from, Vec3d to, @Nullable Entity context) {
        if (from.squaredDistanceTo(to) < LOS_EPSILON * LOS_EPSILON) {
            return true;
        }
        RaycastContext raycast = context == null
                ? new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                ShapeContext.absent())
                : new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                context);
        HitResult hit = world.raycast(raycast);
        return hit.getType() == HitResult.Type.MISS
                || from.squaredDistanceTo(hit.getPos()) >= from.squaredDistanceTo(to) - LOS_EPSILON;
    }

    private static boolean isFinite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
