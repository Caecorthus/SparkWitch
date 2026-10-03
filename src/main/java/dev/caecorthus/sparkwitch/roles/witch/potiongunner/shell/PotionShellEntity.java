package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A fired Potion Gunner shell. Server-authoritative: only the server resolves impact and the blast; the client only
 * predicts flight and draws the tinted trail. The shell type is carried by the synced item stack. It is a role-owned
 * entity, never Wathe's grenade, so grenade-only trait and addon hooks never apply; it is never saved.
 * 发射出的药炮手炮弹。由服务端权威决定：只有服务端结算命中与爆炸；客户端只预测飞行并绘制着色烟迹。炮弹种类由同步的
 * 物品堆携带。它是本职业自有实体而非 Wathe 手雷，因此只针对手雷的词条与附属模组钩子永不生效；也从不存盘。
 */
public class PotionShellEntity extends ThrownItemEntity {
    /**
     * Launch position, synced so client prediction (including a client that starts tracking mid-flight) measures the
     * flat range from the same point as the server; NaN until set.
     * 发射位置，经同步使客户端预测（包括飞行途中才开始追踪的客户端）与服务端从同一点量取平直射程；未设置时为 NaN。
     */
    private static final TrackedData<Vector3f> LAUNCH_ORIGIN =
            DataTracker.registerData(PotionShellEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    /** Trail dust per tick, spread along the tick's path (the shell moves 2.5 blocks a tick). / 每刻沿路径分布的烟迹数。 */
    private static final int TRAIL_STEPS = 4;
    private static final float TRAIL_SCALE = 1.0F;
    private static final float SMOKE_CHANCE = 0.5F;

    /** Server only, set at launch; shells are never saved, so it never needs NBT. / 仅服务端，发射时写入；炮弹不存盘，无需 NBT。 */
    @Nullable
    private UUID gunnerUuid;
    private boolean detonated;
    /** Both sides: latched on the first non-flat tick. / 两端：在第一个非平直刻锁定。 */
    private boolean ballistic;
    /**
     * Server only: the velocity a Rift Gate left the shell with during this tick's move ({@link #onDeflected}); null on
     * every other tick. / 仅服务端：本刻移动中裂隙门留给炮弹的速度（见 {@link #onDeflected}）；其余各刻为 null。
     */
    @Nullable
    private Vec3d riftGateVelocity;

    public PotionShellEntity(EntityType<? extends PotionShellEntity> type, World world) {
        super(type, world);
    }

    public PotionShellEntity(World world, LivingEntity owner) {
        super(PotionGunnerEntities.potionShell(), owner, world);
        this.gunnerUuid = owner.getUuid();
    }

    /**
     * Spawns a shell of {@code type} from the gunner's eye along {@code yaw}/{@code pitch}. Returns false when nothing
     * was spawned, so the caller keeps the loaded shell.
     * 沿 {@code yaw}/{@code pitch} 从药炮手眼部发射一颗 {@code type} 炮弹。未生成时返回 false，调用方保留已装填的炮弹。
     */
    public static boolean launch(ServerPlayerEntity gunner, PotionShellType type, float yaw, float pitch) {
        if (gunner == null || type == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !(gunner.getWorld() instanceof ServerWorld world) || !isRoundActive(world)) {
            return false;
        }
        // The ThrownEntity owner constructor starts the shell at the eye minus 0.1, like the Wathe grenade.
        // ThrownEntity 的拥有者构造器把炮弹放在眼部下方 0.1 处，与 Wathe 手雷一致。
        PotionShellEntity shell = new PotionShellEntity(world, gunner);
        shell.setItem(new ItemStack(SparkWitchItems.potionShell(type)));
        shell.setLaunchOrigin(shell.getPos());
        // Exact ballistics for the scope ticks: no divergence and, unlike setVelocity(Entity, …), no inherited
        // shooter motion. / 为瞄准镜刻度保证精确弹道：无散布，且不像 setVelocity(Entity, …) 那样继承射手的运动。
        Vec3d direction = PotionBlastGeometry.direction(yaw, pitch);
        shell.setVelocity(direction.x, direction.y, direction.z, PotionGunnerRules.MUZZLE_SPEED, 0.0F);
        return world.spawnEntity(shell);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(LAUNCH_ORIGIN, new Vector3f(Float.NaN, Float.NaN, Float.NaN));
    }

    @Override
    public void tick() {
        if (!getWorld().isClient()) {
            // D-R1: once the round leaves ACTIVE (STOPPING: the winner is decided) a shell still in flight is
            // discarded without exploding, so no kill, gold or bounty lands after the result; the finalize sweep covers
            // unticked ones. / D-R1：对局一旦离开 ACTIVE（STOPPING：胜负已定），仍在飞行的炮弹直接移除、不爆炸，
            // 结果确定后不会再有击杀、金币或赏金；未被 tick 的炮弹由收尾清理处理。
            if (!isRoundActive(getWorld())) {
                discard();
                return;
            }
            if (launchOrigin() == null) {
                // A shell not spawned by launch (e.g. summoned) measures its flat range from where it first ticks.
                // 非 launch 生成的炮弹（如指令召唤）从首次 tick 的位置开始量取平直射程。
                setLaunchOrigin(getPos());
            }
            if (age >= PotionGunnerRules.SHELL_LIFETIME_TICKS) {
                detonate(getPos());
                return;
            }
            // Seeker seam (server): a Seeker device in this tick's path breaks and the shell bursts at it.
            // 搜寻者接缝（服务端）：本刻路径上的搜寻者设备被打坏，炮弹在该处爆炸。
            Vec3d device = SeekerDeviceHits.onPotionShellSweep(this, getOwner(), getPos(),
                    getPos().add(getVelocity()));
            if (device != null) {
                detonate(device);
                return;
            }
        }
        // Flat flight (addendum 1): ThrownEntity#tick moves by the pre-tick velocity, then applies its 0.99 drag
        // inline (0.8 in water; there is no drag hook) and gravity through getGravity(). A flat tick therefore
        // snapshots the velocity and restores it after super.tick(), which cancels both in one place; the step itself
        // is unchanged. Both sides run this from the synced launch origin, so prediction matches the server.
        // 平直飞行（补充 1）：ThrownEntity#tick 先按本刻开始时的速度移动，再内联施加 0.99 阻力（水中 0.8，没有阻力钩子），
        // 并经 getGravity() 施加重力。因此平直刻在 super.tick() 前记下速度、之后原样恢复，一处同时抵消阻力与重力；
        // 位移本身不变。两端都基于同步的发射点执行，预测与服务端一致。
        boolean flat = isFlatTick();
        Vec3d stepVelocity = getVelocity();
        Vec3d stepStart = getPos();
        riftGateVelocity = null;
        super.tick();
        if (flat && !isRemoved()) {
            if (riftGateVelocity != null) {
                // Rift Gate seam (D18, server): the gate moved the shell inside super.tick(), which then applied drag
                // and gravity to the gate's exit velocity. A flat tick restores that exit velocity, never the pre-gate
                // one, and the flat range carries over the jump.
                // 裂隙门接缝（D18，服务端）：门在 super.tick() 中移动了炮弹，随后原版对门给出的出口速度施加了阻力与重力。
                // 平直刻恢复的是这个出口速度而不是进门前的速度，平直射程也随这次跳转延续。
                stepVelocity = riftGateVelocity;
                carryFlatRangeThroughRiftGate(stepStart, stepVelocity);
            }
            setVelocity(stepVelocity);
        }
        if (getWorld().isClient() && !isRemoved()) {
            spawnTrail();
        }
    }

    /**
     * {@link PotionGunnerRules#isFlatTick} at the start of a tick, before this tick's move, on the snapped path length
     * ({@link PotionShellFlight}); latches once the shell leaves the flat range.
     * 本刻开始、移动之前按对齐后的路径长度套用 {@link PotionGunnerRules#isFlatTick}；离开平直射程后锁定。
     */
    private boolean isFlatTick() {
        if (ballistic) {
            return false;
        }
        Vec3d origin = launchOrigin();
        if (origin == null) {
            // A client that has not received the origin yet uses vanilla physics; the server corrects it.
            // 尚未收到发射点的客户端按原版物理预测，由服务端纠正。
            return false;
        }
        if (PotionShellFlight.isFlatTick(getPos().distanceTo(origin))) {
            return true;
        }
        ballistic = true;
        return false;
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
     * Rift Gate seam (owner decision D18), server only: after a gate moved the shell on a flat tick, the synced launch
     * origin is re-based straight behind the shell's new position along its new velocity, as far back as the path flown
     * so far ({@link PotionShellFlight#flatPathAfterTick}). {@link #isFlatTick} then keeps counting the same path on
     * both sides, so a gate neither restarts the 50-block flat range nor ends it early: 20 flat ticks in total.
     * 裂隙门接缝（所有者决定 D18），仅服务端：门在平直刻移动炮弹后，把同步的发射点重设到炮弹新位置沿新速度方向的正后方，
     * 距离等于已飞过的路径长度（{@link PotionShellFlight#flatPathAfterTick}）。此后 {@link #isFlatTick} 在两端继续累计同一条
     * 路径，因此穿门既不会重新开始 50 格平直射程，也不会让它提前结束：总共仍是 20 个平直刻。
     */
    private void carryFlatRangeThroughRiftGate(Vec3d stepStart, Vec3d exitVelocity) {
        Vec3d origin = launchOrigin();
        if (origin == null) {
            return;
        }
        double path = PotionShellFlight.flatPathAfterTick(stepStart.distanceTo(origin));
        setLaunchOrigin(getPos().subtract(exitVelocity.normalize().multiply(path)));
    }

    /**
     * D-R4: the shell passes through everything its blast could never catch. Wathe corpses ({@link PlayerBodyEntity},
     * a {@code LivingEntity}) are transparent, and so are players who are spectators, creative, or not
     * {@code isPlayerPlayingAndAlive} (Wathe-dead, e.g. Wraiths); both sides decide these from synced state, so client
     * prediction matches. SparkTraits Last Escape is server-only (like {@code ShockDeviceEntity}): the client keeps
     * flying through until the server's position corrects it. Every other living player stops the shell, allies
     * included (it is a physical shell); doors stop it through the shared {@code RaycastShapeScope}.
     * D-R4：炮弹穿过所有其爆炸永远波及不到的对象。Wathe 尸体（{@link PlayerBodyEntity}，属于 {@code LivingEntity}）
     * 对炮弹透明；旁观、创造模式以及不满足 {@code isPlayerPlayingAndAlive}（Wathe 判定死亡，如冤魂）的玩家同样透明；
     * 两端都依据同步状态判定，客户端预测一致。SparkTraits 最后逃脱只在服务端判定（与 {@code ShockDeviceEntity} 相同）：
     * 客户端继续飞行，直到服务端位置纠正。其余存活玩家都会挡下炮弹，包括己方（这是实体炮弹）；门通过共享的
     * {@code RaycastShapeScope} 挡住炮弹。
     */
    @Override
    protected boolean canHit(Entity entity) {
        if (!super.canHit(entity) || entity instanceof PlayerBodyEntity) {
            return false;
        }
        if (!(entity instanceof PlayerEntity player)) {
            return true;
        }
        return PotionShellFlight.playerStopsShell(
                GameFunctions.isPlayerAliveAndSurvival(player),
                GameFunctions.isPlayerPlayingAndAlive(player),
                !getWorld().isClient() && SparkTraitsKillerBridge.isLastEscapeActive(player));
    }

    /**
     * Rift Gate seam (Riftwalker owner decision D18). {@link #canHit} needs no gate clause: every non-player entity
     * already stops the shell, and a gate answers that hit with its deflection (a jump to another gate, a reflection
     * when there is none, or a capped pass) in place of {@link #onCollision}, so nothing bursts at the gate. Vanilla
     * calls this on the server only, inside {@code super.tick()} and after the gate set the new position and velocity;
     * it only records that velocity for {@link #tick}. Every other deflector keeps vanilla behaviour.
     * 裂隙门接缝（隙行者所有者决定 D18）。{@link #canHit} 无需为门单独处理：所有非玩家实体本来就会挡下炮弹，而门用自己的偏转
     * （跳到另一扇门、没有其他门时反弹，或达到上限后穿过）取代 {@link #onCollision} 处理这次命中，因此炮弹不会在门处爆炸。
     * 原版只在服务端、于 {@code super.tick()} 之内且门已写入新位置与速度之后调用本方法；这里只记下该速度供 {@link #tick} 使用。
     * 其他偏转者保持原版行为。
     */
    @Override
    protected void onDeflected(@Nullable Entity deflector, boolean fromAttack) {
        super.onDeflected(deflector, fromAttack);
        if (deflector instanceof RiftGateEntity) {
            riftGateVelocity = getVelocity();
        }
    }

    /**
     * D-R1: shells launch, fly and burst only while Wathe's round is exactly {@code ACTIVE}.
     * D-R1：炮弹只在 Wathe 对局恰为 {@code ACTIVE} 时发射、飞行与爆炸。
     */
    static boolean isRoundActive(World world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // Centre before vanilla handlers run, while the velocity is still the incoming one. / 在原版处理前计算爆心。
        Vec3d center = impactCenter(hitResult);
        super.onCollision(hitResult);
        if (getWorld().isClient()) {
            return;
        }
        detonate(center);
    }

    private Vec3d impactCenter(HitResult hitResult) {
        if (hitResult instanceof EntityHitResult entityHit) {
            return PotionBlastGeometry.entityImpact(getPos(), getPos().add(getVelocity()),
                    entityHit.getEntity().getBoundingBox());
        }
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            Direction side = blockHit.getSide();
            return PotionBlastGeometry.blockImpact(hitResult.getPos(), side.getOffsetX(), side.getOffsetY(),
                    side.getOffsetZ());
        }
        return hitResult.getPos();
    }

    /** Server: detonates at most once, then discards. / 服务端：至多引爆一次，随后移除。 */
    private void detonate(Vec3d center) {
        if (detonated || isRemoved()) {
            return;
        }
        detonated = true;
        PotionBlastService.detonate(this, center);
        discard();
    }

    private void spawnTrail() {
        float[] rgb = PotionShellFlight.trailRgb(renderType().color());
        DustParticleEffect dust = new DustParticleEffect(new Vector3f(rgb[0], rgb[1], rgb[2]), TRAIL_SCALE);
        Vec3d from = new Vec3d(prevX, prevY, prevZ);
        Vec3d step = getPos().subtract(from).multiply(1.0 / TRAIL_STEPS);
        for (int i = 0; i < TRAIL_STEPS; i++) {
            Vec3d point = from.add(step.multiply(i));
            getWorld().addParticle(dust, point.x, point.y, point.z, 0.0, 0.0, 0.0);
        }
        if (random.nextFloat() < SMOKE_CHANCE) {
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    /** Server type from the stack; null for a shell without a shell item (fails closed: no blast). / 服务端炮弹种类。 */
    @Nullable
    public PotionShellType shellTypeOrNull() {
        return getStack().getItem() instanceof PotionShellItem item ? item.shellType() : null;
    }

    /** Client rendering type; TR only as a fallback. / 客户端渲染用种类；仅在缺省时回退为 TR。 */
    public PotionShellType renderType() {
        PotionShellType type = shellTypeOrNull();
        return type == null ? PotionShellType.TR : type;
    }

    /** Server only. / 仅服务端。 */
    @Nullable
    public UUID gunnerUuid() {
        return gunnerUuid;
    }

    /**
     * Round-end sweep for the WP1 lifecycle at Wathe's finalize (the game-stop event carries no world): no shell
     * carries into the lobby or the next round.
     * 回合结束清理，供生命周期在 Wathe 收尾时调用（游戏停止事件不携带世界）：任何炮弹都不会带入大厅或下一局。
     */
    public static void discardAll(ServerWorld world) {
        for (PotionShellEntity shell
                : world.getEntitiesByType(PotionGunnerEntities.potionShell(), EntityPredicates.VALID_ENTITY)) {
            shell.discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return SparkWitchItems.potionShell(PotionShellType.TR);
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
