package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile.RiftGateProjectileService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Frozen contract (G0) for a placed Rift Gate (裂隙门), modelled on {@code SeekerDeviceEntity}. The gate number, the
 * placer UUID and the match id are server-only fields, never in the DataTracker, so no client learns who placed a gate;
 * the only public tracked value is {@link #FACING}. Gates never take damage, are never pushed or collided with, never
 * save, and are removed only through {@link RiftGateRegistry#close} or the round-edge sweeps (D4). A right-click on the
 * gate is the entry request ({@link RiftSessionService#tryEnter}); projectiles are handled by the vanilla deflection
 * seam ({@link RiftGateProjectileService}). Owned by P1 (body, box, self-check), P4 (projectile hooks), P6 (visibility).
 * 已放置裂隙门的冻结契约（G0），仿照 {@code SeekerDeviceEntity}。门编号、放置者 UUID 与对局 id 仅存于服务端，从不进入
 * DataTracker，任何客户端都无法得知门由谁放置；唯一公开的同步值是 {@link #FACING}。门从不受伤、不被推动或碰撞、从不存盘，
 * 只经 {@link RiftGateRegistry#close} 或对局边界清扫移除（D4）。右键门即进门请求（{@link RiftSessionService#tryEnter}）；
 * 投掷物经原版偏转接缝处理（{@link RiftGateProjectileService}）。归属：P1（主体、碰撞箱、自检）、P4（投掷物钩子）、P6（可见性）。
 */
public class RiftGateEntity extends Entity {
    /** Horizontal facing; the model front (and projectile exit side) points this way. / 水平朝向；模型正面（投掷物出口侧）朝向。 */
    public static final TrackedData<Direction> FACING =
            DataTracker.registerData(RiftGateEntity.class, TrackedDataHandlerRegistry.FACING);
    /** Gates and their outline render to 64 blocks regardless of box size. / 门及其描边渲染到 64 格，与碰撞箱大小无关。 */
    private static final double RENDER_DISTANCE_SQUARED = 64.0 * 64.0;

    private int gateNumber;
    @Nullable
    private UUID placer;
    @Nullable
    private String matchId;

    public RiftGateEntity(EntityType<? extends RiftGateEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    // ---- Server-only identity (never tracked) / 仅服务端身份（从不同步） ----

    /**
     * Server only; called once by the placement service before spawning. {@code matchId} is
     * {@code SeekerTargeting.currentMatchId}-style (Wathe replay match UUID string, null when no match).
     * 仅服务端；由放置服务在生成前调用一次。{@code matchId} 与 {@code SeekerTargeting.currentMatchId} 同格式。
     */
    public void initServerState(int gateNumber, UUID placer, @Nullable String matchId) {
        this.gateNumber = gateNumber;
        this.placer = placer;
        this.matchId = matchId;
    }

    /** Server only (0 on clients and before init). / 仅服务端（客户端及初始化前为 0）。 */
    public int gateNumber() {
        return gateNumber;
    }

    /** Server only. / 仅服务端。 */
    @Nullable
    public UUID placer() {
        return placer;
    }

    /** Server only. / 仅服务端。 */
    @Nullable
    public String matchId() {
        return matchId;
    }

    /** Both sides. / 双端。 */
    public Direction facing() {
        return dataTracker.get(FACING);
    }

    /** Server only; horizontal directions only (anything else becomes NORTH). / 仅服务端；只接受水平方向。 */
    public void setFacing(Direction facing) {
        dataTracker.set(FACING, facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
    }

    /** Round-edge sweep of every gate entity in one world. / 对局边界清扫某世界的所有门实体。 */
    public static void discardAll(ServerWorld world) {
        List<? extends RiftGateEntity> gates = world.getEntitiesByType(
                TypeFilter.instanceOf(RiftGateEntity.class), EntityPredicates.VALID_ENTITY);
        for (RiftGateEntity gate : gates) {
            gate.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld serverWorld) {
            // P1 self-check (round running, match id, registry still lists this gate). / P1 自检。
            if (!isRemoved() && !RiftGateLifecycle.passesSelfCheck(serverWorld, this)) {
                discard();
                return;
            }
            // P4: catch projectiles that skip entity collision (SparkStrength M67). / P4：捕获不走实体碰撞的投掷物。
            if (!isRemoved()) {
                RiftGateProjectileService.sweepUncollidable(this);
            }
        } else {
            RiftGateClientBridge.clientTick(this);
        }
    }

    // ---- Entry (right-click) / 进门（右键） ----

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient()) {
            // The interact packet is already sent; never predict entry. / 交互包已发送；客户端从不预测进门。
            return ActionResult.CONSUME;
        }
        return player instanceof ServerPlayerEntity serverPlayer
                ? RiftSessionService.tryEnter(serverPlayer, this)
                : ActionResult.PASS;
    }

    /** Crosshair-targetable so a right-click reaches {@link #interact}. / 可被准星选中，右键才能到达 interact。 */
    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Left-clicks are ignored. / 忽略左键攻击。 */
    @Override
    public boolean handleAttack(Entity attacker) {
        return true;
    }

    // ---- Projectiles (P4) / 投掷物（P4） ----

    @Override
    public boolean canBeHitByProjectile() {
        return !isRemoved() && RiftGateProjectileService.isProjectileTarget(this);
    }

    @Override
    public ProjectileDeflection getProjectileDeflection(ProjectileEntity projectile) {
        return RiftGateProjectileService.DEFLECTION;
    }

    // ---- Indestructible, never an obstacle (Seeker device set) / 不可破坏、从不阻挡（搜寻者设备同款） ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    protected MoveEffect getMoveEffect() {
        return MoveEffect.NONE;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // ---- Box and rendering / 碰撞箱与渲染 ----

    // TODO(P1): override calculateBoundingBox() to the facing-rotated GATE_WIDTH x GATE_HEIGHT x GATE_DEPTH slab and
    //  refresh it in onTrackedDataSet(FACING). / 按朝向旋转的薄板碰撞箱，并在 FACING 变化时刷新。

    /** Covers model B (2.25 tall, 1×1 floor circle) so the renderer is never culled early. / 覆盖模型 B，避免过早剔除。 */
    @Override
    public Box getVisibilityBoundingBox() {
        return new Box(getX() - 0.75, getY() - 0.25, getZ() - 0.75, getX() + 0.75, getY() + 2.5, getZ() + 0.75);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < RENDER_DISTANCE_SQUARED;
    }

    // ---- Persistence: never saved / 持久化：从不存盘 ----

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(FACING, Direction.NORTH);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
