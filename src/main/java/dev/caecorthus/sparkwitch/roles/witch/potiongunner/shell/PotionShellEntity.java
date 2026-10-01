package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
                || !(gunner.getWorld() instanceof ServerWorld world)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
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
            // A shell still flying when the round stops is dropped at once; the finalize sweep covers unticked ones.
            // 对局停止时仍在飞行的炮弹立即移除；未被 tick 的炮弹由收尾清理处理。
            if (!GameWorldComponent.KEY.get(getWorld()).isRunning()) {
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
        super.tick();
        if (flat && !isRemoved()) {
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
     * Spectators, creative players and dead players are transparent; every other player body stops the shell, allies
     * included (it is a physical shell). SparkFactionAPI already removes vetoed players (Wraiths) from player-owned
     * projectile collision queries on the server; doors stop it through the shared {@code RaycastShapeScope}.
     * 旁观、创造与死亡玩家对炮弹透明；其余玩家的身体都会挡下炮弹，包括己方（这是实体炮弹）。SparkFactionAPI 已在服务端把被
     * 否决的玩家（冤魂）从玩家投射物的碰撞查询中移除；门通过共享的 {@code RaycastShapeScope} 挡住炮弹。
     */
    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity)
                && (!(entity instanceof PlayerEntity player) || GameFunctions.isPlayerAliveAndSurvival(player));
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
