package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearItems;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A fired 40 mm smoke round ({@code sparkwitch:smoke_round_40mm}). Server-authoritative: only the server decides a
 * burst; both sides run the same flight ({@link M32Ballistics} step, {@link SmokeRoundFlight} arming and bounce) so the
 * client's prediction matches, and the server pushes a position/velocity resync on every bounce. It never damages
 * anything (G1): it overrides {@link #onCollision} without calling vanilla's handlers, so no block reaction (a pot
 * breaking, a bell) or entity hit runs. G3: an impact before {@link BearRules#ARMING_DISTANCE} blocks of path bounces
 * (damped) without bursting; after that the first block or entity impact bursts it; the backup fuse bursts it on its
 * {@link BearRules#BACKUP_FUSE_TICKS}th tick wherever it is. A burst is {@code SmokeCloudService.spawn} at the point
 * stepped out of the surface plus the public burst sound and a small puff; an off-match presentation round (D11) only
 * plays the sound and the puff, never a cloud. A match round discards itself without bursting once the round leaves
 * ACTIVE (so no cloud lands after the result). Never saved, so a round that flies out of entity-ticking range stays in
 * memory unticked: {@link #discardAll} sweeps the loaded ones at Wathe's finalize, and one that only ticks again later
 * (this round or a later one) is {@link SmokeRoundFlight#stale} by world time and removes itself unburst.
 * 发射出的 40 mm 烟雾榴弹（{@code sparkwitch:smoke_round_40mm}）。服务端权威：只有服务端决定爆开；两端执行相同的飞行（
 * {@link M32Ballistics} 步进、{@link SmokeRoundFlight} 解除保险与反弹），使客户端预测一致，且服务端在每次反弹时推送位置/速度
 * 重新同步。它从不伤害任何东西（G1）：它覆盖 {@link #onCollision} 且不调用原版处理，因此不会触发任何方块反应（打碎陶罐、敲响钟）或
 * 实体命中。G3：路径不足 {@link BearRules#ARMING_DISTANCE} 格时的撞击只会（阻尼）反弹而不爆开；此后首次撞击方块或实体即爆开；备用
 * 引信在第 {@link BearRules#BACKUP_FUSE_TICKS} 刻无论在何处都会引爆。爆开即在退出表面的点调用 {@code SmokeCloudService.spawn}，并
 * 播放公开的爆开声与小团烟雾粒子；场外仅表现的榴弹（D11）只播放声音与粒子，从不产生烟团。对局榴弹在对局离开 ACTIVE 后直接移除、
 * 不爆开（结果确定后不再出现烟团）。从不存盘，因此飞出实体 tick 范围的榴弹会留在内存里不再 tick：{@link #discardAll} 在 Wathe
 * 收尾时清扫已加载的榴弹；之后（本局或之后的对局）才重新 tick 的榴弹按世界时间判定为 {@link SmokeRoundFlight#stale}，直接移除、
 * 不爆开。
 */
public class SmokeRoundEntity extends ThrownItemEntity {
    /**
     * Launch position, synced so a client that starts tracking after the first move (the spawn packet carries the
     * post-move position) still counts the arming path from the muzzle; NaN until set.
     * 发射位置，经同步使在第一次移动之后才开始追踪的客户端（生成包携带移动后的位置）仍从炮口起计算解除保险路径；未设置时为 NaN。
     */
    private static final TrackedData<Vector3f> LAUNCH_ORIGIN =
            DataTracker.registerData(SmokeRoundEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final double PLAYER_HIT_MARGIN = 0.3;
    private static final double MOVING_SQUARED = 1.0E-8;
    private static final int PUFF_PARTICLES = 14;

    /** Server only, set at launch. / 仅服务端，发射时写入。 */
    @Nullable
    private UUID shooterUuid;
    /** Server only: an off-match presentation round (no cloud). / 仅服务端：场外仅表现的榴弹（无烟团）。 */
    private boolean presentation;
    /**
     * Server only, never saved: world time at launch (at the first server tick for a round not spawned by
     * {@link #launch}); {@code Long.MIN_VALUE} until set. Read by the {@link SmokeRoundFlight#stale} check.
     * 仅服务端，从不存盘：发射时的世界时间（非 {@link #launch} 生成的榴弹取首个服务端 tick）；未设置时为
     * {@code Long.MIN_VALUE}。供 {@link SmokeRoundFlight#stale} 检查读取。
     */
    private long launchTime = Long.MIN_VALUE;
    private boolean burst;
    /** Path flown so far; NaN on a client until its first tick. / 已飞行的路径；客户端首刻之前为 NaN。 */
    private double travelled = Double.NaN;
    /** Lies still (a bounced round that stopped) until the fuse. / 静止（反弹后停下的榴弹），直到引信引爆。 */
    private boolean resting;
    /** Client only: an armed impact, waiting for the server's burst. / 仅客户端：已解除保险的撞击，等待服务端爆开。 */
    private boolean frozen;
    // Per-tick scratch set inside super.tick(). / super.tick() 内写入的逐刻临时状态。
    @Nullable
    private Vec3d bouncePoint;
    @Nullable
    private Vec3d bounceVelocity;
    @Nullable
    private Vec3d bounceNormal;
    @Nullable
    private Vec3d riftGateVelocity;

    public SmokeRoundEntity(EntityType<? extends SmokeRoundEntity> type, World world) {
        super(type, world);
    }

    public SmokeRoundEntity(World world, LivingEntity owner) {
        super(BearEntities.SMOKE_ROUND, owner, world);
        this.shooterUuid = owner.getUuid();
    }

    /**
     * Spawns a round from the shooter's server-side eye (minus vanilla's 0.1) along {@code direction} at exactly
     * {@link M32Ballistics#MUZZLE_SPEED}: no divergence and no inherited shooter motion, so the sight's holdover is
     * exact. A match round needs an ACTIVE round; a {@code presentation} round spawns in any state. Returns false when
     * nothing spawned, so the caller keeps the cylinder as it was.
     * 从射手服务端眼部（减去原版的 0.1）沿 {@code direction} 以恰好 {@link M32Ballistics#MUZZLE_SPEED} 发射一发：无散布、不继承射手
     * 运动，因此瞄具的抬高量是精确的。对局榴弹需要 ACTIVE 对局；{@code presentation} 榴弹在任何状态下都可生成。未生成时返回 false，
     * 调用方保持转轮不变。
     */
    public static boolean launch(ServerPlayerEntity shooter, Vec3d direction, boolean presentation) {
        if (shooter == null || direction == null || !(direction.lengthSquared() > MOVING_SQUARED)
                || !(shooter.getWorld() instanceof ServerWorld world) || !presentation && !isRoundActive(world)) {
            return false;
        }
        SmokeRoundEntity round = new SmokeRoundEntity(world, shooter);
        round.presentation = presentation;
        round.travelled = 0.0;
        round.launchTime = world.getTime();
        round.setItem(new ItemStack(BearItems.SMOKE_ROUND));
        round.setLaunchOrigin(round.getPos());
        Vec3d unit = direction.normalize();
        round.setVelocity(unit.x, unit.y, unit.z, (float) M32Ballistics.MUZZLE_SPEED, 0.0F);
        return world.spawnEntity(round);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(LAUNCH_ORIGIN, new Vector3f(Float.NaN, Float.NaN, Float.NaN));
    }

    @Override
    public void tick() {
        boolean client = getWorld().isClient();
        if (!client) {
            if (!presentation && !isRoundActive(getWorld())) {
                discard();
                return;
            }
            long now = getWorld().getTime();
            if (launchTime == Long.MIN_VALUE) {
                launchTime = now;
            } else if (SmokeRoundFlight.stale(now - launchTime)) {
                // It missed ticks outside entity-ticking range (possibly a whole round ago): never resume or burst.
                // 它在实体 tick 范围之外漏过了 tick（可能已隔了一整局）：绝不恢复飞行或爆开。
                discard();
                return;
            }
            if (launchOrigin() == null) {
                // A round not spawned by launch (e.g. summoned) arms from where it first ticks.
                // 非 launch 生成的榴弹（如指令召唤）从首次 tick 的位置开始计算解除保险路径。
                setLaunchOrigin(getPos());
            }
            if (SmokeRoundFlight.fuseDue(age)) {
                burst(getPos(), getVelocity());
                return;
            }
        }
        if (Double.isNaN(travelled)) {
            Vec3d origin = launchOrigin();
            travelled = origin == null ? 0.0 : getPos().distanceTo(origin);
        }
        if ((resting || frozen) && getVelocity().lengthSquared() > MOVING_SQUARED) {
            // Something moved it (a server resync after a misprediction, an explosion's push): fly on.
            // 有东西让它动了（预测错误后的服务端重新同步、爆炸推力）：继续飞行。
            resting = false;
            frozen = false;
        }
        if (resting || frozen) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        Vec3d stepStart = getPos();
        Vec3d stepVelocity = getVelocity();
        bouncePoint = null;
        bounceVelocity = null;
        bounceNormal = null;
        riftGateVelocity = null;
        // ThrownEntity#tick resolves this tick's collision first (onCollision / a Rift Gate deflection), then moves by
        // the velocity it then has and applies its own 0.99 drag and 0.03 gravity, which are replaced below.
        // ThrownEntity#tick 先结算本刻碰撞（onCollision / 裂隙门偏转），再按此时的速度移动并施加自身的 0.99 阻力与 0.03 重力，
        // 下面会替换它们。
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (frozen) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (bouncePoint != null && bounceVelocity != null) {
            travelled += stepStart.distanceTo(bouncePoint);
            setPosition(bouncePoint);
            if (bounceNormal != null && SmokeRoundFlight.comesToRest(bounceVelocity, bounceNormal)) {
                resting = true;
                setVelocity(Vec3d.ZERO);
            } else {
                setVelocity(bounceVelocity);
            }
            if (!client) {
                // Resync clients now instead of at the next tracking interval. / 立即重新同步客户端，而不是等下一次追踪间隔。
                velocityDirty = true;
            }
            return;
        }
        travelled += stepVelocity.length();
        // Rift Gate seam (Riftwalker D18): the gate's exit velocity replaces the pre-tick one. / 裂隙门接缝：以门的出口速度为准。
        setVelocity(M32Ballistics.nextVelocity(riftGateVelocity != null ? riftGateVelocity : stepVelocity));
    }

    /**
     * Never calls vanilla's handlers (G1: no damage, no block reaction). Armed: the server bursts the round at the entry
     * point; the client freezes it there until the removal arrives. Unarmed: a damped bounce off the entered face.
     * 从不调用原版处理（G1：无伤害、无方块反应）。已解除保险：服务端在进入点引爆榴弹；客户端将其冻结在该处直到收到移除。未解除保险：
     * 从进入的表面阻尼反弹。
     */
    @Override
    protected void onCollision(HitResult hitResult) {
        if (burst || isRemoved() || hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3d from = getPos();
        Vec3d velocity = getVelocity();
        Vec3d impact;
        Vec3d normal;
        if (hitResult instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox().expand(PLAYER_HIT_MARGIN);
            impact = box.raycast(from, from.add(velocity)).orElse(box.contains(from) ? from : box.getCenter());
            normal = SmokeRoundFlight.faceNormal(box, impact);
        } else if (hitResult instanceof BlockHitResult blockHit) {
            impact = blockHit.getPos();
            Direction side = blockHit.getSide();
            normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        } else {
            return;
        }
        double before = Double.isNaN(travelled) ? 0.0 : travelled;
        if (SmokeRoundFlight.armed(before, from.distanceTo(impact))) {
            if (getWorld().isClient()) {
                frozen = true;
                setPosition(impact);
                setVelocity(Vec3d.ZERO);
            } else {
                burst(impact, velocity);
            }
            return;
        }
        bounceNormal = normal;
        bounceVelocity = SmokeRoundFlight.bounce(velocity, normal);
        bouncePoint = impact.add(normal.multiply(SmokeRoundFlight.SURFACE_OFFSET));
    }

    /**
     * Rift Gate seam (Riftwalker D18): vanilla calls this on the server inside {@code super.tick()} after the gate set
     * the new position and velocity; only that velocity is recorded for {@link #tick}.
     * 裂隙门接缝（隙行者 D18）：原版仅在服务端、于 {@code super.tick()} 内门写入新位置与速度之后调用本方法；这里只记下该速度供
     * {@link #tick} 使用。
     */
    @Override
    protected void onDeflected(@Nullable Entity deflector, boolean fromAttack) {
        super.onDeflected(deflector, fromAttack);
        if (deflector instanceof RiftGateEntity) {
            riftGateVelocity = getVelocity();
        }
    }

    /**
     * What stops the round: corpses never, a Raven Form crow only when SparkFactionAPI lets the shooter affect its
     * owner, a player only per {@link SmokeRoundFlight#playerStopsRound}; every other hittable entity does.
     * 什么会挡下榴弹：尸体永远不会；化鸦乌鸦仅当 SparkFactionAPI 允许射手影响其主人时会；玩家按
     * {@link SmokeRoundFlight#playerStopsRound} 判定；其余可被命中的实体都会。
     */
    @Override
    protected boolean canHit(Entity entity) {
        if (!super.canHit(entity) || entity instanceof PlayerBodyEntity) {
            return false;
        }
        if (!BlackRavenCrowHits.projectileMayHit(getOwner(), entity)) {
            return false;
        }
        if (!(entity instanceof PlayerEntity player)) {
            return true;
        }
        return SmokeRoundFlight.playerStopsRound(GameFunctions.isPlayerAliveAndSurvival(player),
                GameFunctions.isPlayerPlayingAndAlive(player));
    }

    /** Server: bursts at most once, then discards. / 服务端：至多爆开一次，随后移除。 */
    private void burst(Vec3d impact, Vec3d direction) {
        if (burst || isRemoved()) {
            return;
        }
        burst = true;
        if (getWorld() instanceof ServerWorld world) {
            Vec3d point = SmokeRoundFlight.burstPoint(impact, direction);
            if (!presentation) {
                SmokeCloudService.spawn(world, point,
                        direction != null && direction.lengthSquared() > MOVING_SQUARED ? direction : null,
                        shooterUuid);
            }
            world.playSound(null, point.x, point.y, point.z, SparkWitchSounds.BEAR_SMOKE_BURST, SoundCategory.PLAYERS,
                    2.0F, 0.95F + random.nextFloat() * 0.1F);
            world.spawnParticles(ParticleTypes.POOF, point.x, point.y, point.z, PUFF_PARTICLES, 0.2, 0.2, 0.2, 0.03);
        }
        discard();
    }

    @Nullable
    private Vec3d launchOrigin() {
        Vector3f origin = getDataTracker().get(LAUNCH_ORIGIN);
        if (!Float.isFinite(origin.x) || !Float.isFinite(origin.y) || !Float.isFinite(origin.z)) {
            return null;
        }
        return new Vec3d(origin.x, origin.y, origin.z);
    }

    private void setLaunchOrigin(Vec3d origin) {
        getDataTracker().set(LAUNCH_ORIGIN, new Vector3f((float) origin.x, (float) origin.y, (float) origin.z));
    }

    /**
     * Round-end sweep for {@code BearLifecycleService} at Wathe's finalize (the Potion Gunner shell's model): no loaded
     * round, flying, resting or frozen in a loaded chunk that no longer ticks entities, carries into the lobby or the
     * next round. A round in an unloaded chunk is not reachable here; the {@link SmokeRoundFlight#stale} check in
     * {@link #tick} removes it when it ticks again.
     * 供 {@code BearLifecycleService} 在 Wathe 收尾时调用的回合结束清扫（沿用药炮手炮弹的做法）：任何已加载的榴弹，无论在飞、
     * 静止还是停在不再 tick 实体的已加载区块里，都不会带入大厅或下一局。未加载区块中的榴弹在这里够不到；它再次 tick 时由
     * {@link #tick} 中的 {@link SmokeRoundFlight#stale} 检查移除。
     */
    public static void discardAll(ServerWorld world) {
        for (SmokeRoundEntity round
                : world.getEntitiesByType(BearEntities.SMOKE_ROUND, EntityPredicates.VALID_ENTITY)) {
            round.discard();
        }
    }

    /** Match rounds fly and burst only while Wathe's round is exactly ACTIVE. / 对局榴弹只在对局恰为 ACTIVE 时飞行与爆开。 */
    static boolean isRoundActive(World world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** Both sides: the round lies still. / 两端：榴弹静止。 */
    public boolean isResting() {
        return resting;
    }

    @Override
    protected Item getDefaultItem() {
        return BearItems.SMOKE_ROUND;
    }

    /** Rounds in flight never outlive a save. / 飞行中的榴弹从不存盘。 */
    @Override
    public boolean shouldSave() {
        return false;
    }
}
