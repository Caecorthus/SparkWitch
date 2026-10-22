package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEndReason;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackManager;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server queries every crow hit entry shares, and the owner substitution that lets a weapon's own player path kill the
 * Black Raven. Server thread only.
 * 所有乌鸦命中入口共用的服务端查询，以及让武器自身的玩家路径处死黑羽鸦的“主人替身”机制。仅服务端线程。
 *
 * <p>Owner substitution (stable contract): a weapon whose victim pick found a crow hands its player code the crow's
 * owner ({@link #substitute}) instead of a separate kill, so every rule that weapon applies to a player victim runs
 * unchanged on the Black Raven: Wathe's innocent-shot punishment (小脑), backfire and mood loss, the USEC, Swordfish
 * and Mighty Force misfire rules, faction vetoes, Vendetta isolation, Judge attribution, rewards and records. The
 * substitution only remembers the crow for this server tick; when that weapon then calls
 * {@code GameFunctions.killPlayer} on the owner, {@code mixin/blackraven/BlackRavenCrowKillLinkMixin} (HEAD of the
 * 5-argument {@code killPlayer}) first ends the session with {@code CROW_KILLED}, exactly the first half of
 * {@link BlackRavenCrowSessions#killOwnerVia}; the kill itself is the weapon's, and the corpse lies at the body. A
 * substitution that never reaches {@code killPlayer} (a backfire, a refused target) expires at the end of the tick.
 * Weapons without a reusable player path (the bat, sphere blasts, add-on guns) call {@code killOwnerVia} directly.
 * 主人替身（稳定契约）：武器在选取受害者时找到乌鸦，就把乌鸦的主人交给其玩家代码（{@link #substitute}），而不另行击杀，
 * 因此该武器对玩家受害者施加的每条规则都原样作用于黑羽鸦：Wathe 误杀惩罚（小脑）、走火与理智损失，USEC、剑鱼与巨力的误杀规则，
 * 阵营否决、复仇者隔离、审判官归因、奖励与记录。替身只在本服务端刻内记住乌鸦；随后该武器对主人调用
 * {@code GameFunctions.killPlayer} 时，{@code mixin/blackraven/BlackRavenCrowKillLinkMixin}（5 参数 {@code killPlayer} 的 HEAD）
 * 先以 {@code CROW_KILLED} 结束会话，正是 {@link BlackRavenCrowSessions#killOwnerVia} 的前半段；击杀本身属于武器，尸体位于本体处。
 * 没有走到 {@code killPlayer} 的替身（走火、目标被拒）在本刻结束时失效。没有可复用玩家路径的武器（球棒、球形爆炸、附属枪械）
 * 直接调用 {@code killOwnerVia}。</p>
 */
public final class BlackRavenCrowTargets {
    /** Search padding around a segment, covering every crow box it can touch. / 线段搜索外扩量。 */
    private static final double SEARCH_PADDING = 1.0;
    /** Pending kill links: owner UUID → the substituted crow, valid for one server tick. / 待生效的击杀链接，仅本刻有效。 */
    private static final Map<UUID, Substitution> SUBSTITUTIONS = new HashMap<>();
    /**
     * Victims of every {@code killPlayer} attempt inside an open blast scope (the Wathe grenade's own loop), or null.
     * 处于打开的爆炸作用域（Wathe 手雷自身的循环）内时，所有 {@code killPlayer} 尝试的受害者；否则为 null。
     */
    private static @Nullable Set<UUID> blastAttempts;
    private static boolean registered;

    /** A live crow some attacker may hit, with its live owner. / 某攻击者可命中的存活乌鸦及其在线主人。 */
    public record Target(BlackRavenCrowEntity crow, ServerPlayerEntity owner) {
    }

    /** A crow on a ray, with the squared distance where the ray enters it. / 射线上的乌鸦及射线进入处的平方距离。 */
    public record RayHit(Target target, double distanceSquared) {
    }

    private record Substitution(int crowEntityId, Vec3d crowPos, long time) {
    }

    private BlackRavenCrowTargets() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SUBSTITUTIONS.clear();
            blastAttempts = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SUBSTITUTIONS.clear();
            blastAttempts = null;
        });
    }

    // ---- Who may be hit / 谁可以被命中 ----

    /**
     * Server: {@code entity} as a crow {@code attacker} may hit, else null. The crow must be live (its owner's session
     * still flies it) and in the attacker's world; a player attacker must be alive in survival or adventure and never
     * the owner (the body cannot use items anyway); a null attacker is an ownerless blast.
     * 服务端：{@code entity} 是 {@code attacker} 可命中的乌鸦时返回目标，否则为 null。乌鸦须存活（主人会话仍在操控）且与攻击者
     * 同一世界；玩家攻击者须存活且非旁观/创造，且绝不是主人本人（本体本就不能使用物品）；null 攻击者表示无主爆炸。
     */
    public static @Nullable Target hittable(@Nullable Entity attacker, @Nullable Entity entity) {
        if (!(entity instanceof BlackRavenCrowEntity crow) || crow.getWorld().isClient() || crow.isRemoved()) {
            return null;
        }
        ServerPlayerEntity owner = BlackRavenCrowSessions.ownerOf(crow);
        if (owner == null || owner.getWorld() != crow.getWorld()) {
            return null;
        }
        if (attacker != null) {
            if (attacker.getWorld() != crow.getWorld() || attacker.getUuid().equals(owner.getUuid())
                    || attacker instanceof PlayerEntity player && !GameFunctions.isPlayerAliveAndSurvival(player)) {
                return null;
            }
        }
        return new Target(crow, owner);
    }

    /** Every live crow in {@code world} that {@code attacker} may hit. / {@code world} 中 {@code attacker} 可命中的所有存活乌鸦。 */
    public static List<Target> live(ServerWorld world, @Nullable Entity attacker) {
        List<Target> targets = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            Target target = hittable(attacker, BlackRavenCrowSessions.crowOf(player));
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    /** {@link #live} without the crows whose owner {@link #mayAffect} refuses for {@code action}. / 去掉被拒乌鸦的 live。 */
    public static List<Target> live(ServerWorld world, @Nullable Entity attacker, @Nullable Identifier action) {
        List<Target> targets = live(world, attacker);
        targets.removeIf(target -> !mayAffect(attacker, target.owner(), action));
        return targets;
    }

    /**
     * Cheap pre-check for the per-shot entries: whether {@code attacker}'s world has any crow it may hit.
     * 每次射击入口的廉价预检：{@code attacker} 所在世界是否有其可命中的乌鸦。
     */
    public static boolean anyLive(ServerPlayerEntity attacker) {
        return attacker.getWorld() instanceof ServerWorld world && !live(world, attacker).isEmpty();
    }

    /**
     * External seam (SparkFactionAPI, deny wins): whether {@code attacker} may affect the crow's {@code owner} with
     * {@code action}, exactly as SparkFactionAPI judges a hit on the owner's body ({@code PlayerAffectMixinGuard}: no
     * player attacker, the owner itself or a null action always pass). SparkFactionAPI's own guards resolve only player
     * ids (Wathe's gun/knife receivers, projectile collisions, the {@code killPlayer} HEAD veto that would cancel the
     * kill after our session end), so every entry where a crow hit becomes an owner hit asks this first, and a refused
     * hit leaves the crow and its session alone.
     * 外部接缝（SparkFactionAPI，拒绝优先）：{@code attacker} 能否以 {@code action} 影响乌鸦的主人 {@code owner}，判定与
     * SparkFactionAPI 对命中本体的判定完全一致（{@code PlayerAffectMixinGuard}：非玩家攻击者、主人本人或 null 行为一律放行）。
     * SparkFactionAPI 自身的守卫只解析玩家 id（Wathe 枪/刀接收器、投射物碰撞，以及会在会话已结束后才取消击杀的
     * {@code killPlayer} HEAD 否决），因此每个把乌鸦命中变为主人命中的入口都先询问此处，被拒的命中不触碰乌鸦及其会话。
     */
    public static boolean mayAffect(@Nullable Entity attacker, @Nullable ServerPlayerEntity owner,
                                    @Nullable Identifier action) {
        if (!(attacker instanceof PlayerEntity player) || owner == null || action == null
                || player.getUuid().equals(owner.getUuid())) {
            return true;
        }
        return SparkFactionApi.canAffectPlayer(player, owner, action, GameWorldComponent.KEY.get(owner.getWorld()));
    }

    /** {@link #hittable} plus {@link #mayAffect} for {@code action}. / {@link #hittable} 加上对 action 的许可判定。 */
    public static @Nullable Target hittable(@Nullable Entity attacker, @Nullable Entity entity,
                                            @Nullable Identifier action) {
        Target target = hittable(attacker, entity);
        return target != null && mayAffect(attacker, target.owner(), action) ? target : null;
    }

    // ---- Owner substitution / 主人替身 ----

    /**
     * Hands {@code target}'s owner to the weapon's own player path and arms the kill link for this tick (class doc).
     * 把 {@code target} 的主人交给武器自身的玩家路径，并为本刻装好击杀链接（见类说明）。
     */
    public static ServerPlayerEntity substitute(Target target) {
        BlackRavenCrowEntity crow = target.crow();
        SUBSTITUTIONS.put(target.owner().getUuid(),
                new Substitution(crow.getId(), crow.getPos(), crow.getWorld().getTime()));
        return target.owner();
    }

    /**
     * Drops a pending substitution of {@code entity}: a receiver that resolved a real player id (a direct body hit)
     * measures that player normally, even right after a crow hit on the same owner this tick.
     * 丢弃 {@code entity} 的待生效替身：接收器解析到真实玩家 id（直接命中本体）时按常规量取该玩家，即使本刻刚命中过同一主人的乌鸦。
     */
    public static void forget(@Nullable Entity entity) {
        if (entity instanceof ServerPlayerEntity player && !SUBSTITUTIONS.isEmpty()) {
            SUBSTITUTIONS.remove(player.getUuid());
        }
    }

    /** True while {@code entity} is an owner substituted this tick. / {@code entity} 是本刻被替身的主人时为真。 */
    public static boolean isSubstituted(@Nullable Entity entity) {
        return substitution(entity) != null;
    }

    /**
     * Where the substituted crow was when it was hit (for the weapon's own impact sound), else null.
     * 被替身的乌鸦被命中时所在位置（供武器自身的命中声使用）；否则为 null。
     */
    public static @Nullable Vec3d substitutedCrowPos(@Nullable Entity entity) {
        Substitution substitution = substitution(entity);
        return substitution == null ? null : substitution.crowPos();
    }

    /**
     * Kill link, from {@code BlackRavenCrowKillLinkMixin} at the HEAD of every {@code killPlayer}: a victim substituted
     * this tick loses its session first ({@code CROW_KILLED}, no Homing Feather), as {@code killOwnerVia} does, unless
     * SparkFactionAPI refuses {@code killer} on it for {@code deathReason} ({@link #mayAffect}): that kill is cancelled
     * by SparkFactionAPI's own HEAD veto, so the crow and its session stay. Also records the victim of an open blast
     * scope ({@link #recordBlastAttempts}).
     * 击杀链接，由 {@code BlackRavenCrowKillLinkMixin} 在每次 {@code killPlayer} 的 HEAD 调用：本刻被替身的受害者先结束会话
     * （{@code CROW_KILLED}，不使用归羽），与 {@code killOwnerVia} 一致；但 SparkFactionAPI 以 {@code deathReason} 拒绝
     * {@code killer} 影响它时（{@link #mayAffect}），该击杀会被 SparkFactionAPI 自身的 HEAD 否决取消，乌鸦与会话保持不变。
     * 同时记录打开的爆炸作用域中的受害者（{@link #recordBlastAttempts}）。
     */
    public static void beforeKillPlayer(@Nullable ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        @Nullable Identifier deathReason) {
        if (victim == null) {
            return;
        }
        if (blastAttempts != null) {
            blastAttempts.add(victim.getUuid());
        }
        if (SUBSTITUTIONS.isEmpty()) {
            return;
        }
        Substitution substitution = substitution(victim);
        if (substitution == null) {
            return;
        }
        SUBSTITUTIONS.remove(victim.getUuid());
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(victim);
        if (crow != null && crow.getId() == substitution.crowEntityId() && mayAffect(killer, victim, deathReason)) {
            BlackRavenCrowSessions.end(victim, BlackRavenCrowEndReason.CROW_KILLED);
        }
    }

    /**
     * Runs {@code blast} (a weapon's own kill loop) and returns the UUID of every victim it tried to kill, through the
     * kill link, so a crow pass after it never hits a Black Raven twice for one blast. Server thread; not reentrant
     * (an inner scope only adds to the outer one); the end-of-tick sweep closes a scope an exception left open.
     * 执行 {@code blast}（武器自身的击杀循环），并经击杀链接返回其尝试击杀的每个受害者的 UUID，使其后的乌鸦扫描不会因同一次
     * 爆炸两次命中同一黑羽鸦。服务端线程；不可重入（内层作用域只并入外层）；异常遗留的作用域由刻末清扫关闭。
     */
    public static Set<UUID> recordBlastAttempts(Runnable blast) {
        if (blastAttempts != null) {
            Set<UUID> outer = blastAttempts;
            blast.run();
            return Set.copyOf(outer);
        }
        blastAttempts = new HashSet<>();
        try {
            blast.run();
            return Set.copyOf(blastAttempts);
        } finally {
            blastAttempts = null;
        }
    }

    private static @Nullable Substitution substitution(@Nullable Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || SUBSTITUTIONS.isEmpty()) {
            return null;
        }
        Substitution substitution = SUBSTITUTIONS.get(player.getUuid());
        return substitution != null && substitution.time() == player.getWorld().getTime() ? substitution : null;
    }

    // ---- Ray queries / 射线查询 ----

    /**
     * The nearest crow {@code shooter} may hit whose lag-compensated volumes (grown by {@code growth}) the segment
     * {@code start -> end} enters strictly nearer than {@code beatSquared}; null otherwise. The caller clips the
     * segment to blocks, so a crow behind a wall is never hit.
     * {@code shooter} 可命中、且线段 {@code start -> end} 严格早于 {@code beatSquared} 进入其延迟补偿体积（按 {@code growth}
     * 扩大）的最近乌鸦；否则为 null。调用方负责按方块截断线段，因此墙后的乌鸦永远打不到。
     */
    public static @Nullable RayHit nearestOnRay(ServerPlayerEntity shooter, Vec3d start, Vec3d end, double growth,
                                                double beatSquared) {
        if (!(shooter.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        RayHit best = null;
        double closest = beatSquared;
        for (Target target : live(world, shooter)) {
            double entry = BlackRavenCrowHitRules.entrySquared(start, end,
                    BlackRavenCrowHitboxHistory.hitVolumes(shooter, target.crow(), growth));
            if (BlackRavenCrowHitRules.crowWins(entry, closest)) {
                closest = entry;
                best = new RayHit(target, entry);
            }
        }
        return best;
    }

    /**
     * Squared entry of the segment into {@code target}'s lag-compensated volumes (grown by {@code growth}); -1 on a
     * miss. / 线段进入 {@code target} 延迟补偿体积（按 {@code growth} 扩大）处的平方距离；未命中为 -1。
     */
    public static double entrySquared(ServerPlayerEntity shooter, Target target, Vec3d start, Vec3d end,
                                      double growth) {
        return BlackRavenCrowHitRules.entrySquared(start, end,
                BlackRavenCrowHitboxHistory.hitVolumes(shooter, target.crow(), growth));
    }

    /**
     * Server melee re-check for a client-picked crow: the nearest of its lag-compensated volumes within {@code reach}
     * of the attacker's eye, and a clear COLLIDER line from the eye to the crow's current box or to a point of the
     * nearest volume. / 客户端选中乌鸦时的服务端近战复查：其延迟补偿体积中离攻击者眼睛最近者在 {@code reach} 内，且眼睛到乌鸦
     * 当前箱体或最近体积某点的 COLLIDER 视线畅通。
     */
    public static boolean inMeleeReach(ServerPlayerEntity attacker, Target target, double reach) {
        Vec3d eye = attacker.getEyePos();
        Box nearest = null;
        double closest = Double.POSITIVE_INFINITY;
        for (Box volume : BlackRavenCrowHitboxHistory.hitVolumes(attacker, target.crow(), 0.0)) {
            double distance = volume.squaredMagnitude(eye);
            if (distance < closest) {
                closest = distance;
                nearest = volume;
            }
        }
        if (nearest == null || !BlackRavenCrowHitRules.withinReach(closest, reach)) {
            return false;
        }
        return visibleFrom(attacker.getWorld(), eye, List.of(target.crow().getBoundingBox(), nearest), attacker);
    }

    /**
     * Server confirm for a client-picked hitscan shot on a crow (revolver, derringer, add-on guns): the crow within
     * {@code maxDistance}, and the shooter's look ray (clipped at the first block) entering its lag-compensated volumes
     * grown by {@link BlackRavenCrowHitRules#GUN_CONFIRM_GROWTH}.
     * 客户端选中乌鸦时的服务端即时射击确认（左轮、德林加、附属枪械）：乌鸦在 {@code maxDistance} 内，且射手视线射线（在第一个
     * 方块处截断）进入其按 {@link BlackRavenCrowHitRules#GUN_CONFIRM_GROWTH} 扩大的延迟补偿体积。
     */
    public static boolean gunAimed(ServerPlayerEntity shooter, Target target, double maxDistance) {
        if (!(maxDistance > 0.0) || shooter.distanceTo(target.crow()) >= maxDistance) {
            return false;
        }
        Vec3d eye = shooter.getEyePos();
        Vec3d end = clipToBlocks(shooter.getWorld(), eye,
                eye.add(shooter.getRotationVec(1.0F).multiply(maxDistance)), shooter);
        return entrySquared(shooter, target, eye, end, BlackRavenCrowHitRules.GUN_CONFIRM_GROWTH) >= 0.0;
    }

    // ---- Competitors / 竞争对象 ----

    /**
     * Squared entry of the segment into the nearest live Magician puppet {@code attacker} may end (box grown by
     * {@code growth}, the puppet hooks' own measure); +inf when none.
     * 线段进入 {@code attacker} 可结束的最近存活魔术师皮套（箱体按 {@code growth} 扩大，即皮套钩子自身的量法）处的平方距离；
     * 没有时为正无穷。
     */
    public static double puppetSquared(ServerPlayerEntity attacker, Vec3d start, Vec3d end, double growth) {
        if (!(attacker.getWorld() instanceof ServerWorld world)) {
            return Double.POSITIVE_INFINITY;
        }
        double nearest = Double.POSITIVE_INFINITY;
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            if (!MagicianPuppetHits.isHittablePuppet(attacker, puppet)) {
                continue;
            }
            double entry = BlackRavenCrowHitRules.entrySquared(start, end,
                    List.of(puppet.getBoundingBox().expand(Math.max(0.0, growth))));
            if (entry >= 0.0 && entry < nearest) {
                nearest = entry;
            }
        }
        return nearest;
    }

    /**
     * Squared entry of the segment into the nearest live Seeker device {@code attacker} may break (margin-grown box,
     * the device hooks' own measure); +inf when none. A device the attacker may not break is transparent, as in the
     * device hooks. External seam: SparkStrength's {@code @Pseudo} {@code DroneSparkWitchCrowTargetsMixin} lowers
     * the result at RETURN to its nearest live Bomber drone on the block-clipped segment, so a nearer drone beats the
     * crow wherever the crow skips the device step (shotgun, Feather Blade, projectile and axe sweeps) and then absorbs
     * in that device step; keep the name and the {@code (ServerPlayerEntity, Vec3d, Vec3d)} signature.
     * 线段进入 {@code attacker} 可打坏的最近存活搜寻者设备（按瞄准余量扩大，即设备钩子自身的量法）处的平方距离；没有时为
     * 正无穷。攻击者不能打坏的设备是透明的，与设备钩子一致。外部接缝：SparkStrength 的 {@code @Pseudo}
     * {@code DroneSparkWitchCrowTargetsMixin} 在 RETURN 处把结果压低到按方块截断的线段上最近的存活炸弹客无人机，因此在乌鸦会
     * 跳过设备步骤之处（猎枪、羽刃、投射物与飞斧扫掠），更近的无人机胜过乌鸦，随后在该设备步骤中吸收；保持方法名与
     * {@code (ServerPlayerEntity, Vec3d, Vec3d)} 签名不变。
     */
    public static double deviceSquared(ServerPlayerEntity attacker, Vec3d start, Vec3d end) {
        World world = attacker.getWorld();
        List<SeekerDeviceEntity> devices = world.getEntitiesByClass(SeekerDeviceEntity.class,
                new Box(start, end).expand(SEARCH_PADDING), device -> device.isAlive() && !device.isRemoved());
        if (devices.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        double nearest = Double.POSITIVE_INFINITY;
        for (SeekerDeviceEntity device : devices) {
            double entry = BlackRavenCrowHitRules.entrySquared(start, end,
                    List.of(device.getBoundingBox().expand(Math.max(0.0, device.targetingMargin()))));
            if (entry >= 0.0 && entry < nearest && !SeekerDeviceRaycast.isOwnDevice(attacker, device)
                    && SeekerDamageRules.mayBreak(attacker, SeekerDeviceService.findOwner(device), game)) {
                nearest = entry;
            }
        }
        return nearest;
    }

    // ---- Geometry helpers / 几何辅助 ----

    /** {@code end} cut at the first COLLIDER block. / 在第一个 COLLIDER 方块处截断的终点。 */
    public static Vec3d clipToBlocks(World world, Vec3d start, Vec3d end, Entity context) {
        HitResult block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, context));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    /**
     * True when a COLLIDER line from {@code from} reaches some sample point of any volume (the Seeker sample set: centre,
     * face centres and corners, each inset). / 从 {@code from} 出发的 COLLIDER 线段能到达任一体积的某个采样点时为真。
     */
    public static boolean visibleFrom(World world, Vec3d from, List<Box> volumes, @Nullable Entity context) {
        for (Box volume : volumes) {
            if (SeekerDamageRules.hasLineOfSight(world, from, volume, context)) {
                return true;
            }
        }
        return false;
    }
}
