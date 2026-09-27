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
    /** Aim tolerance between the shooter's look vector and the device (latency). / 射手视线与设备方向的夹角容差（延迟）。 */
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
     * Line-of-sight sample points of a device box: centre, top centre and two opposite top corners, inset slightly.
     * 设备箱体的视线采样点：中心、顶面中心与两个对角顶角（略微内缩）。
     */
    public static List<Vec3d> samplePoints(Box box) {
        double inset = Math.min(SAMPLE_INSET, Math.min(box.getLengthX(), Math.min(box.getLengthY(), box.getLengthZ())) / 2.0);
        double top = box.maxY - inset;
        Vec3d center = box.getCenter();
        return List.of(
                center,
                new Vec3d(center.x, top, center.z),
                new Vec3d(box.minX + inset, top, box.minZ + inset),
                new Vec3d(box.maxX - inset, top, box.maxZ - inset));
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

    // ---- World line of sight (server) ----

    /**
     * True when at least one sample point of {@code box} is visible from {@code from} through COLLIDER shapes.
     * {@code context} may be null (absent shape context, e.g. a blast centre).
     * 当 {@code box} 至少有一个采样点能从 {@code from} 经 COLLIDER 形状看到时为真；{@code context} 可为空（如爆心）。
     */
    public static boolean hasLineOfSight(World world, Vec3d from, Box box, @Nullable Entity context) {
        for (Vec3d point : samplePoints(box)) {
            if (segmentClear(world, from, point, context)) {
                return true;
            }
        }
        return false;
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
