package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The crow a Black Raven flies in Raven Form ({@code sparkwitch:black_raven_crow}). Never saved, never summonable, no
 * gravity, collides with blocks only, never pushed, ray-targetable, and immune to every environmental damage.
 * 黑羽鸦化鸦时操控的乌鸦（{@code sparkwitch:black_raven_crow}）。从不存盘、不可召唤、无重力、只与方块碰撞、不被推动、
 * 可被射线选中，并免疫一切环境伤害。
 *
 * <p>Authority: the server owns ownership, lifetime and death. Only the owner's client moves the crow
 * (client-predicted absolute poses on {@code sparkwitch:black_raven_crow_move}); the server replays each pose through
 * {@link #applyPilotStep} (step caps, move budget, bounds clamp, block collision) and answers a rejected step with
 * {@code sparkwitch:black_raven_crow_correction}. Every other client interpolates tracker updates.
 * 权威划分：归属、存续与死亡由服务端决定。只有主人客户端移动乌鸦（经 {@code black_raven_crow_move} 上报客户端预测的绝对位姿）；
 * 服务端用 {@link #applyPilotStep} 重放每个位姿（步长上限、移动预算、边界限制、方块碰撞），被拒的位移以
 * {@code black_raven_crow_correction} 回复。其他客户端只插值追踪更新。</p>
 *
 * <p>Owner: the owner UUID is tracked data. This leaks nothing: the crow is visible to everyone and Wathe already syncs
 * every RAW role to every client. Hits: Wathe weapons reach the crow through
 * {@link BlackRavenCrowSessions#killOwnerVia}; vanilla {@link #damage} routes only a bat melee or a player-lethal
 * explosion there and ignores everything else.
 * 主人：主人 UUID 存于追踪数据。这不泄露信息：乌鸦对所有人可见，Wathe 本就向所有客户端同步每个原始职业。命中：Wathe 武器经
 * {@link BlackRavenCrowSessions#killOwnerVia} 命中乌鸦；原版 {@link #damage} 只把球棒近战或足以致死玩家的爆炸转到该接口，其余全部忽略。</p>
 */
public class BlackRavenCrowEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(
            BlackRavenCrowEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    /**
     * Client bridge installed by crow-client: true when the local player flies this crow, so tracker position updates
     * are ignored for it (the owner's client is the movement authority). Compare entity ids or the owner UUID, never
     * cached instances. Consulted only on the client: an integrated server shares the JVM.
     * 由 crow-client 安装的客户端桥接：本地玩家正在操控此乌鸦时返回 true，从而忽略其追踪位置更新（主人客户端是移动权威）。
     * 须比较实体 id 或主人 UUID，不能缓存实例。只在客户端查询：集成服务端与客户端共享 JVM。
     */
    private static volatile Predicate<BlackRavenCrowEntity> clientPilotHook = crow -> false;

    // Server-only flight validation. / 仅服务端的飞行校验。
    private double horizontalBudget;
    private double verticalBudget;
    private long moveBudgetTime = -1L;
    private long moveTick = -1L;
    private int movesThisTick;
    private @Nullable PilotPose deferredPose;

    // Client-only interpolation. / 仅客户端插值。
    private int lerpTicks;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;
    private float lerpPitch;

    public BlackRavenCrowEntity(EntityType<? extends BlackRavenCrowEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public static void installClientPilotHook(Predicate<BlackRavenCrowEntity> hook) {
        clientPilotHook = hook == null ? crow -> false : hook;
    }

    /** Both sides. / 两端通用。 */
    public @Nullable UUID ownerUuid() {
        return dataTracker.get(OWNER).orElse(null);
    }

    /** Server only; called once before the spawn. / 仅服务端；生成前调用一次。 */
    void setOwner(UUID owner) {
        dataTracker.set(OWNER, Optional.of(owner));
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return player != null && player.equals(ownerUuid());
    }

    /** Client only: whether the local player flies this crow. / 仅客户端：本地玩家是否正在操控此乌鸦。 */
    public boolean isLocallyPiloted() {
        return getWorld().isClient() && clientPilotHook.test(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) {
            clientInterpolate();
            return;
        }
        // Self-check: the owner's live session must still name this crow. / 自检：主人的有效会话必须仍指向此乌鸦。
        if (!isRemoved() && getWorld() instanceof ServerWorld world && !BlackRavenCrowSessions.isLiveCrow(world, this)) {
            discard();
            return;
        }
        // Apply this tick's merged excess move, if any. / 应用本刻合并的超量移动（如有）。
        BlackRavenCrowFlightService.applyDeferredMove(this);
    }

    // ---- Flight / 飞行 ----

    /** One owner-reported absolute pose. / 主人上报的一个绝对位姿。 */
    record PilotPose(Vec3d target, float yaw, float pitch) {
    }

    /**
     * Server: per-tick move rate cap ({@link BlackRavenCrowFlightRules#MAX_MOVES_PER_TICK}); false means the caller
     * defers the move ({@link #deferMove}). A new tick drops a pose still deferred from an older one (the new move is
     * newer). / 服务端：每刻移动包数量上限；返回 false 时由调用方推迟该移动。新的一刻会丢弃更早一刻仍在推迟的位姿（新移动更新）。
     */
    boolean acceptMove(long now) {
        if (now != moveTick) {
            moveTick = now;
            movesThisTick = 0;
            deferredPose = null;
        }
        return ++movesThisTick <= BlackRavenCrowFlightRules.MAX_MOVES_PER_TICK;
    }

    /**
     * Server: an excess move of this tick merges into the one pending latest pose (absolute poses, so the latest wins).
     * 服务端：本刻超量的移动合并为唯一待应用的最新位姿（绝对位姿，最新者为准）。
     */
    void deferMove(PilotPose pose) {
        deferredPose = pose;
    }

    /** Server: takes the pending merged pose, if any. / 服务端：取出待应用的合并位姿（如有）。 */
    @Nullable PilotPose takeDeferredMove() {
        PilotPose pose = deferredPose;
        deferredPose = null;
        return pose;
    }

    /**
     * Server: apply one owner-reported absolute pose. Horizontal and vertical parts are capped separately (up to
     * {@link BlackRavenCrowFlightRules#CATCH_UP_STEPS} caps, each also limited by its refilling budget), clamped to the
     * flight bounds and re-collided with blocks. Returns false when the result differs from the request by more than
     * {@link BlackRavenCrowFlightRules#CORRECTION_EPSILON}, so the caller sends a correction.
     * 服务端：应用主人上报的一个绝对位姿。水平与垂直分量分别限制（最多 {@link BlackRavenCrowFlightRules#CATCH_UP_STEPS} 倍上限，
     * 且各受按刻补充的预算约束），再限制在飞行边界内并重新计算方块碰撞。结果与请求相差超过 CORRECTION_EPSILON 时返回 false，
     * 由调用方发送纠正。
     */
    public boolean applyPilotStep(Vec3d target, float yaw, float pitch) {
        if (getWorld().isClient() || isRemoved()) {
            return true;
        }
        if (!BlackRavenCrowFlightRules.isFinite(target) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return false;
        }
        Vec3d start = getPos();
        long now = getWorld().getTime();
        double maxHorizontal = BlackRavenCrowFlightRules.HORIZONTAL_STEP;
        double maxVertical = BlackRavenCrowFlightRules.VERTICAL_STEP;
        if (moveBudgetTime < 0L) {
            horizontalBudget = BlackRavenCrowFlightRules.fullBudget(maxHorizontal);
            verticalBudget = BlackRavenCrowFlightRules.fullBudget(maxVertical);
        } else {
            horizontalBudget = BlackRavenCrowFlightRules.refillMoveBudget(horizontalBudget, maxHorizontal,
                    now - moveBudgetTime);
            verticalBudget = BlackRavenCrowFlightRules.refillMoveBudget(verticalBudget, maxVertical,
                    now - moveBudgetTime);
        }
        moveBudgetTime = now;
        Vec3d limited = BlackRavenCrowFlightRules.limitStep(target.subtract(start), horizontalBudget, verticalBudget,
                BlackRavenCrowFlightRules.CATCH_UP_STEPS);
        horizontalBudget = Math.max(0.0, horizontalBudget - limited.horizontalLength());
        verticalBudget = Math.max(0.0, verticalBudget - Math.abs(limited.y));
        Vec3d actual = collide(clampToBounds(limited));
        setPosition(start.add(actual));
        setYaw(MathHelper.wrapDegrees(yaw));
        setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        setVelocity(actual);
        return BlackRavenCrowFlightRules.accepted(getPos(), target);
    }

    /**
     * Owner's client only: predict one tick of flight ({@code delta} already capped by the caller with
     * {@link BlackRavenCrowFlightRules}), with the same bounds clamp and block collision as the server, and set the
     * look. Call once per client tick after the world tick; it seeds the previous pose so the camera interpolates.
     * 仅主人客户端：预测一刻飞行（{@code delta} 已由调用方按 {@link BlackRavenCrowFlightRules} 限速），使用与服务端相同的
     * 边界限制与方块碰撞，并设置朝向。请在每个客户端刻的世界刻之后调用一次；它设置上一帧位姿，使镜头平滑插值。
     */
    public void clientPilotMove(Vec3d delta, float yaw, float pitch) {
        if (!getWorld().isClient() || isRemoved() || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        Vec3d start = getPos();
        Vec3d actual = Vec3d.ZERO;
        if (BlackRavenCrowFlightRules.isFinite(delta)
                && delta.lengthSquared() > BlackRavenCrowFlightRules.MIN_MOVE_SQUARED) {
            actual = collide(clampToBounds(delta));
        }
        seedPreviousPose(start);
        setPosition(start.add(actual));
        setYaw(yaw);
        setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        setVelocity(actual);
        lerpTicks = 0;
    }

    /**
     * Owner's client only: apply a server correction at once (the owner ignores tracker updates for its own crow).
     * 仅主人客户端：立即应用服务端纠正（主人会忽略自己乌鸦的追踪更新）。
     */
    public void clientApplyCorrection(double x, double y, double z, float yaw, float pitch) {
        if (!getWorld().isClient() || isRemoved()) {
            return;
        }
        Vec3d corrected = new Vec3d(x, y, z);
        if (!BlackRavenCrowFlightRules.isFinite(corrected)) {
            return;
        }
        seedPreviousPose(corrected);
        setPosition(corrected);
        setYaw(yaw);
        setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        setVelocity(Vec3d.ZERO);
        lerpTicks = 0;
    }

    private void seedPreviousPose(Vec3d previous) {
        prevX = previous.x;
        prevY = previous.y;
        prevZ = previous.z;
        lastRenderX = previous.x;
        lastRenderY = previous.y;
        lastRenderZ = previous.z;
        prevYaw = getYaw();
        prevPitch = getPitch();
    }

    /** Swept block (and world border) collisions only, no entities. / 仅扫掠方块（及世界边界）碰撞，不含实体。 */
    private Vec3d collide(Vec3d movement) {
        if (movement.lengthSquared() <= BlackRavenCrowFlightRules.MIN_MOVE_SQUARED) {
            return Vec3d.ZERO;
        }
        return Entity.adjustMovementForCollisions(this, movement, getBoundingBox(), getWorld(), List.of());
    }

    /** Both sides read the same synced play area and world border. / 两端读取相同的已同步游戏区域与世界边界。 */
    private Vec3d clampToBounds(Vec3d movement) {
        WorldBorder border = getWorld().getWorldBorder();
        Box bounds = BlackRavenCrowFlightRules.bounds(
                MapVariablesWorldComponent.KEY.get(getWorld()).getPlayArea(),
                border.getBoundWest(), border.getBoundNorth(), border.getBoundEast(), border.getBoundSouth());
        return BlackRavenCrowFlightRules.clampToBounds(movement, getBoundingBox(), bounds);
    }

    // ---- Client interpolation / 客户端插值 ----

    @Override
    public boolean isLogicalSideForUpdatingMovement() {
        // The owner's client owns this crow's motion. / 主人客户端掌控其运动。
        return isLocallyPiloted() || super.isLogicalSideForUpdatingMovement();
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                               int interpolationSteps) {
        if (isLocallyPiloted()) {
            lerpTicks = 0;
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpPitch = pitch;
        lerpTicks = Math.max(1, interpolationSteps);
    }

    @Override
    public void setVelocityClient(double x, double y, double z) {
        if (!isLocallyPiloted()) {
            super.setVelocityClient(x, y, z);
        }
    }

    private void clientInterpolate() {
        if (isLocallyPiloted()) {
            lerpTicks = 0;
            return;
        }
        if (lerpTicks > 0) {
            lerpPosAndRotation(lerpTicks, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch);
            lerpTicks--;
        }
    }

    // ---- Hits / 命中 ----

    /**
     * Server: environmental damage (fall, suffocation, fire, drowning, punches, projectiles) is ignored. A bat melee
     * or an explosion lethal to a player kills the owner through {@link BlackRavenCrowSessions#killOwnerVia}
     * ({@link BlackRavenCrowDamage}). Wathe's own melee wrapper never lets a survival player hit a non-player, so the
     * melee branch matters only once crow-hits opens that path.
     * 服务端：环境伤害（摔落、窒息、火焰、溺水、拳击、投射物）一律忽略。球棒近战或足以致死玩家的爆炸经
     * {@link BlackRavenCrowSessions#killOwnerVia} 处死主人（{@link BlackRavenCrowDamage}）。Wathe 自身的近战包装不允许生存模式玩家
     * 攻击非玩家，因此近战分支只有在 crow-hits 打通该路径后才会生效。
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient() || isRemoved()) {
            return false;
        }
        BlackRavenCrowDamage.LethalHit hit = BlackRavenCrowDamage.lethalHit(source, amount);
        if (hit == null) {
            return false;
        }
        BlackRavenCrowSessions.killOwnerVia(this, hit.killer(), hit.deathReason());
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return BlackRavenCrowDamage.lethalHit(source, Float.MAX_VALUE) == null || super.isInvulnerableTo(source);
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
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
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
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

    // ---- Persistence and rendering / 存盘与渲染 ----

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < BlackRavenCrowRules.CROW_RENDER_DISTANCE_SQUARED;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER, Optional.empty());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    /** Server: the owner if online in this world. / 服务端：在线且在同一世界的主人。 */
    @Nullable
    ServerPlayerEntity onlineOwner() {
        UUID owner = ownerUuid();
        if (owner == null || !(getWorld() instanceof ServerWorld world)) {
            return null;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
        return player != null && player.getServerWorld() == world ? player : null;
    }
}
