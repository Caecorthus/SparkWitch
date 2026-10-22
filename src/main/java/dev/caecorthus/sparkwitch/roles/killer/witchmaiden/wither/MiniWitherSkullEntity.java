package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastGeometry;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Mini Wither skull. NORMAL and BLESSED (a Conscience owner) fly at 1 block a tick for 3 s and burst into the 2x2x2
 * cube on the first player, puppet or block they meet; CRIMSON flies at 4 blocks a tick for 48 blocks and kills the
 * first player it meets (owner Q3/Q4). Skulls move in straight lines (no drag, no gravity), never save, and fly only
 * while the round is exactly ACTIVE.
 * 小凋灵的头颅。NORMAL 与 BLESSED（主人善良时）每刻 1 格、飞 3 秒，遇到第一个玩家、皮套或方块时在 2x2x2 立方体内爆炸；
 * CRIMSON 每刻 4 格、飞 48 格，杀死遇到的第一名玩家（所有者 Q3/Q4）。头颅直线飞行（无阻力、无重力），从不存盘，
 * 只在对局恰为 ACTIVE 时飞行。
 */
public class MiniWitherSkullEntity extends ThrownEntity {
    /** Synced for the renderer and trail. / 同步给渲染与尾迹。 */
    private static final TrackedData<Byte> KIND = DataTracker.registerData(MiniWitherSkullEntity.class,
            TrackedDataHandlerRegistry.BYTE);
    /** The margin vanilla projectile collision grows every entity box by. / 原版投射物碰撞为实体箱体增加的余量。 */
    private static final double PLAYER_HIT_MARGIN = 0.3;
    /** SparkFactionAPI's action for player-owned projectiles. / SparkFactionAPI 对玩家发射物使用的行为 id。 */
    private static final Identifier PROJECTILE_ACTION = Identifier.of("sparkfactionapi", "projectile");
    private static final int CRIMSON_TRAIL_STEPS = 6;
    private static final DustParticleEffect CRIMSON_DUST = new DustParticleEffect(new Vector3f(0.85F, 0.1F, 0.08F),
            1.0F);

    public enum Kind {
        NORMAL,
        BLESSED,
        CRIMSON;

        static Kind byId(byte id) {
            Kind[] values = values();
            return id >= 0 && id < values.length ? values[id] : NORMAL;
        }
    }

    /** Server only, set at launch. / 仅服务端，发射时写入。 */
    @Nullable
    private UUID ownerUuid;
    private MiniWitherRules.Mode mode = MiniWitherRules.Mode.HOSTILE;
    private boolean impacted;
    /** Server only: the velocity a Rift Gate left the skull with this tick. / 仅服务端：本刻裂隙门留下的速度。 */
    @Nullable
    private Vec3d riftGateVelocity;

    public MiniWitherSkullEntity(EntityType<? extends MiniWitherSkullEntity> type, World world) {
        super(type, world);
    }

    /**
     * Spawns a skull at {@code from} flying straight toward {@code toward}. Only while the round is exactly ACTIVE.
     * 在 {@code from} 处生成一枚朝 {@code toward} 直线飞行的头颅。仅在对局恰为 ACTIVE 时。
     */
    public static boolean launch(ServerPlayerEntity owner, Kind kind, MiniWitherRules.Mode mode, Vec3d from,
                                 Vec3d toward) {
        if (owner == null || kind == null || from == null || toward == null
                || !(owner.getWorld() instanceof ServerWorld world) || !isRoundActive(world)) {
            return false;
        }
        Vec3d direction = toward.subtract(from);
        if (!(direction.lengthSquared() > 1.0E-6)) {
            direction = owner.getRotationVec(1.0F);
        }
        double speed = kind == Kind.CRIMSON ? MiniWitherRules.CRIMSON_SPEED : MiniWitherRules.SKULL_SPEED;
        MiniWitherSkullEntity skull = new MiniWitherSkullEntity(MiniWitherEntities.skull(), world);
        skull.setOwner(owner);
        skull.ownerUuid = owner.getUuid();
        skull.mode = mode == null ? MiniWitherRules.Mode.HOSTILE : mode;
        skull.getDataTracker().set(KIND, (byte) kind.ordinal());
        skull.refreshPositionAndAngles(from.x, from.y, from.z, 0.0F, 0.0F);
        skull.setVelocity(direction.normalize().multiply(speed));
        skull.updateRotation();
        skull.prevYaw = skull.getYaw();
        skull.prevPitch = skull.getPitch();
        return world.spawnEntity(skull);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, (byte) Kind.NORMAL.ordinal());
    }

    public Kind kind() {
        return Kind.byId(getDataTracker().get(KIND));
    }

    private int lifetime() {
        return kind() == Kind.CRIMSON ? MiniWitherRules.CRIMSON_LIFETIME_TICKS : MiniWitherRules.SKULL_LIFETIME_TICKS;
    }

    @Override
    protected double getGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        if (!getWorld().isClient()) {
            if (!isRoundActive(getWorld())) {
                discard();
                return;
            }
            if (age >= lifetime()) {
                fizzle(getPos());
                return;
            }
            Vec3d from = getPos();
            Vec3d to = from.add(getVelocity());
            // Seeker seam, crimson only (a harmless skull breaks nothing): a device in the path breaks and stops it,
            // unless a puppet its owner may end lies nearer. / 搜寻者接缝，仅赤首（无害的头颅不打坏任何东西）：路径上的
            // 设备被打坏并挡下赤首，除非更近处有主人可结束的皮套。
            // A Raven Form crow before the device is skipped the same way. / 设备之前的化鸦乌鸦同样跳过。
            if (kind() == Kind.CRIMSON && !MagicianPuppetHits.puppetBeforeDevice(this, getOwner(), from, to)
                    && !BlackRavenCrowHits.crowBeforeDevice(this, getOwner(), from, to)) {
                Vec3d device = SeekerDeviceHits.onCrimsonSkullSweep(this, getOwner(), from, to);
                if (device != null) {
                    fizzle(device);
                    return;
                }
            }
            ServerPlayerEntity lagged = lagCompensatedPlayerHit(from, to);
            if (lagged != null) {
                hitPlayer(lagged, PotionBlastGeometry.entityImpact(from, to, lagged.getBoundingBox()));
                return;
            }
        }
        // Straight flight: ThrownEntity applies 0.99 drag inline, so the pre-tick velocity is restored afterwards
        // (or a Rift Gate's exit velocity). / 直线飞行：ThrownEntity 内联施加 0.99 阻力，因此之后恢复本刻开始时的速度
        // （或裂隙门的出口速度）。
        Vec3d velocity = getVelocity();
        riftGateVelocity = null;
        super.tick();
        if (!isRemoved()) {
            setVelocity(riftGateVelocity != null ? riftGateVelocity : velocity);
        }
        if (getWorld().isClient() && !isRemoved()) {
            spawnTrail();
        }
    }

    /**
     * Corpses never stop a skull, nor do players its blast or kill could never reach; the owner and (for a hostile
     * skull) the killer team are passed on the server, and so is a Raven Form crow whose owner SparkFactionAPI shields
     * from the shooter. Every other entity stops it; a Rift Gate deflects it.
     * 尸体永不挡下头颅，爆炸或击杀不可能触及的玩家也一样；服务端还会让主人以及（敌对头颅时）杀手队伍放行，SparkFactionAPI 禁止
     * 射手影响其主人的化鸦乌鸦亦然。其他实体都会挡下头颅；裂隙门会使其偏转。
     */
    @Override
    protected boolean canHit(Entity entity) {
        if (!super.canHit(entity) || entity instanceof PlayerBodyEntity
                || !MagicianPuppetHits.projectileMayHit(getOwner(), entity)
                || !BlackRavenCrowHits.projectileMayHit(getOwner(), entity)) {
            return false;
        }
        if (!(entity instanceof PlayerEntity player)) {
            return true;
        }
        if (!GameFunctions.isPlayerAliveAndSurvival(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        boolean self = player.getUuid().equals(ownerUuid) || player == getOwner();
        if (getWorld().isClient()) {
            return !self;
        }
        if (SparkTraitsKillerBridge.isLastEscapeActive(player)) {
            return false;
        }
        if (kind() == Kind.CRIMSON) {
            return !MiniWitherRules.crimsonPassesThrough(self);
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(getWorld());
        return !MiniWitherRules.skullPassesThrough(mode, self, MiniWitherTargeting.isKillerTeam(player, game));
    }

    @Override
    protected void onDeflected(@Nullable Entity deflector, boolean fromAttack) {
        super.onDeflected(deflector, fromAttack);
        if (deflector instanceof RiftGateEntity) {
            riftGateVelocity = getVelocity();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (getWorld().isClient() || impacted) {
            return;
        }
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            Vec3d at = PotionBlastGeometry.entityImpact(getPos(), getPos().add(getVelocity()), entity.getBoundingBox());
            // Raven Form seam: a crimson skull kills a crow's owner as it kills a player. / 化鸦接缝：赤首像击杀玩家一样处死乌鸦的主人。
            Entity standIn = kind() == Kind.CRIMSON ? BlackRavenCrowHits.projectileStandIn(getOwner(), entity) : entity;
            if (standIn instanceof ServerPlayerEntity player) {
                hitPlayer(player, at);
            } else if (kind() == Kind.CRIMSON
                    && MagicianPuppetHits.onCrimsonSkullHit(this, getOwner(), entityHit)) {
                impacted = true;
                discard();
            } else {
                impact(at);
            }
        } else if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            Direction side = blockHit.getSide();
            impact(PotionBlastGeometry.blockImpact(hitResult.getPos(), side.getOffsetX(), side.getOffsetY(),
                    side.getOffsetZ()));
        }
    }

    /** A player stops the skull: a burst, or the crimson kill. / 玩家挡下头颅：爆炸，或赤首击杀。 */
    private void hitPlayer(ServerPlayerEntity victim, Vec3d at) {
        if (impacted) {
            return;
        }
        if (kind() != Kind.CRIMSON) {
            impact(at);
            return;
        }
        impacted = true;
        ServerWorld world = (ServerWorld) getWorld();
        ServerPlayerEntity owner = getOwner() instanceof ServerPlayerEntity player ? player : null;
        world.spawnParticles(CRIMSON_DUST, at.x, at.y, at.z, 16, 0.2, 0.3, 0.2, 0.0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.5F,
                1.6F);
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        // A non-forced kill with the live owner as killer: money, records and every protection apply, and SparkTraits
        // judges a Conscience owner's innocent victim (小脑) from this killer. / 非强制击杀，凶手为在线主人：金币、记录与
        // 所有防护照常生效，SparkTraits 也据此判定善良主人误杀好人（小脑）。
        if (owner == null || MiniWitherTargeting.mayAffect(owner, victim, game)) {
            GameFunctions.killPlayer(victim, true, owner, SparkWitchDeathReasons.CRIMSON_WITHER_SKULL);
        }
        discard();
    }

    /** NORMAL/BLESSED burst; CRIMSON just fizzles. / NORMAL/BLESSED 爆炸；CRIMSON 只会消散。 */
    private void impact(Vec3d at) {
        if (impacted) {
            return;
        }
        if (kind() == Kind.CRIMSON) {
            fizzle(at);
            return;
        }
        impacted = true;
        MiniWitherBurstService.burst(this, at);
        discard();
    }

    private void fizzle(Vec3d at) {
        impacted = true;
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(kind() == Kind.CRIMSON ? CRIMSON_DUST : ParticleTypes.SMOKE, at.x, at.y, at.z, 6,
                    0.1, 0.1, 0.1, 0.01);
        }
        discard();
    }

    /**
     * Server only: the nearest player whose lag-compensated volume this tick's path enters strictly before vanilla's own
     * collision, so the owner hits where they saw the target (Potion Gunner shell rule); null leaves the tick to vanilla.
     * 仅服务端：本刻路径严格先于原版碰撞进入其延迟补偿体积的最近玩家，主人因此能在自己看到目标的位置命中（药炮手炮弹规则）；
     * 返回 null 时本刻交给原版处理。
     */
    @Nullable
    private ServerPlayerEntity lagCompensatedPlayerHit(Vec3d from, Vec3d to) {
        if (!(getWorld() instanceof ServerWorld world)) {
            return null;
        }
        Entity owner = getOwner();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        ServerPlayerEntity hit = null;
        double hitEntry = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == owner || candidate.getUuid().equals(ownerUuid)) {
                continue;
            }
            double entry = laggedEntrySquared(from, to,
                    PlayerHitboxHistory.projectileHitVolumes(candidate, PLAYER_HIT_MARGIN), age > 0);
            if (entry < 0.0 || entry >= hitEntry || !canHit(candidate)
                    || owner instanceof ServerPlayerEntity shooter
                    && !SparkFactionApi.canAffectPlayer(shooter, candidate, PROJECTILE_ACTION, game)) {
                continue;
            }
            hit = candidate;
            hitEntry = entry;
        }
        if (hit == null || !(hitEntry < vanillaEntrySquared(ProjectileUtil.getCollision(this, this::canHit), from,
                to))) {
            return null;
        }
        return hit;
    }

    /**
     * Entry distance into lag-compensated volumes grown by the margin; a start inside the real box counts only once the
     * skull has moved. -1 on a miss. / 进入按余量扩大的延迟补偿体积的距离；起点位于真实箱体内只在头颅移动过后才算。未命中为 -1。
     */
    static double laggedEntrySquared(Vec3d from, Vec3d to, List<Box> volumes, boolean startInsideCounts) {
        double nearest = -1.0;
        for (Box volume : volumes) {
            Box real = volume.contract(PLAYER_HIT_MARGIN);
            double entry;
            if (real.contains(from)) {
                if (!startInsideCounts) {
                    continue;
                }
                entry = 0.0;
            } else {
                Optional<Vec3d> point = volume.contains(from) ? real.raycast(from, to) : volume.raycast(from, to);
                if (point.isEmpty()) {
                    continue;
                }
                entry = from.squaredDistanceTo(point.get());
            }
            if (nearest < 0.0 || entry < nearest) {
                nearest = entry;
            }
        }
        return nearest;
    }

    private static double vanillaEntrySquared(HitResult vanilla, Vec3d from, Vec3d to) {
        if (vanilla instanceof EntityHitResult entityHit) {
            return Math.max(0.0, HitscanLagRules.entryDistanceSquared(from, to,
                    List.of(entityHit.getEntity().getBoundingBox().expand(PLAYER_HIT_MARGIN))));
        }
        return vanilla.getType() == HitResult.Type.BLOCK
                ? from.squaredDistanceTo(vanilla.getPos())
                : Double.POSITIVE_INFINITY;
    }

    private void spawnTrail() {
        World world = getWorld();
        Entity owner = getOwner();
        Vec3d eye = owner != null ? owner.getEyePos() : null;
        switch (kind()) {
            case CRIMSON -> {
                Vec3d start = new Vec3d(prevX, prevY, prevZ);
                Vec3d step = getPos().subtract(start).multiply(1.0 / CRIMSON_TRAIL_STEPS);
                for (int i = 0; i < CRIMSON_TRAIL_STEPS; i++) {
                    Vec3d point = start.add(step.multiply(i));
                    if (eye == null || !MiniWitherRules.withinOwnerClearance(eye, point)) {
                        world.addParticle(CRIMSON_DUST, point.x, point.y, point.z, 0.0, 0.0, 0.0);
                    }
                }
            }
            case BLESSED -> {
                if (random.nextFloat() < 0.5F && trailVisible(eye)) {
                    world.addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
                }
            }
            default -> {
                if (random.nextFloat() < 0.5F && trailVisible(eye)) {
                    world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private boolean trailVisible(@Nullable Vec3d ownerEye) {
        return ownerEye == null || !MiniWitherRules.withinOwnerClearance(ownerEye, getPos());
    }

    static boolean isRoundActive(World world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** Server only. / 仅服务端。 */
    @Nullable
    UUID ownerUuid() {
        return ownerUuid;
    }

    MiniWitherRules.Mode mode() {
        return mode;
    }

    /** Round-end sweep: no skull outlives the round. / 回合结束清理：任何头颅都不会延续到对局之外。 */
    public static void discardAll(ServerWorld world) {
        for (MiniWitherSkullEntity skull
                : world.getEntitiesByType(MiniWitherEntities.skull(), EntityPredicates.VALID_ENTITY)) {
            skull.discard();
        }
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
