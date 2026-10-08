package dev.caecorthus.sparkwitch.entity;

import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaRules;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.doctor4t.wathe.index.WatheBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The Ninja Grappling Hook head (owner spec 2026-10-07). A plain {@link Entity}, never a {@code ProjectileEntity}, so
 * Rift Gate projectile teleport/deflection, Magician projectile hits and vanilla projectile mixins never see it. The
 * server alone flies it (block-only ray, it passes through every entity), latches it, breaks the chain and pulls the
 * owner by velocity; the client only predicts the flight and renders the head and chain. It never outlives one hook
 * cycle and is never saved.
 * 忍者钩爪的钩头（所有者 2026-10-07 规格）。普通 {@link Entity}，而非 {@code ProjectileEntity}，因此裂隙门投射物传送/偏转、
 * 魔术师投射物命中与原版投射物 mixin 都不会处理它。只有服务端负责飞行（只检测方块的射线，穿过所有实体）、钩住、断链，
 * 并以速度拉拽持有者；客户端只预测飞行并渲染钩头与铁链。它不会跨越一次钩爪循环，也从不保存。
 */
public final class NinjaGrapplingHookEntity extends Entity {
    /** Synced to clients; the renderer reads it. Append new states only. / 同步给客户端供渲染读取；只能在末尾追加新状态。 */
    public enum State {
        FLYING,
        LATCHED,
        PULLING
    }

    private static final TrackedData<Byte> STATE =
            DataTracker.registerData(NinjaGrapplingHookEntity.class, TrackedDataHandlerRegistry.BYTE);
    /**
     * Where the owner's feet are pulled, set when PULLING starts, as an offset from the hook (floats stay exact near
     * the hook even on far-out maps). The owner's client steers toward it locally
     * (`client/ninja/NinjaGrappleClientPull`), so a round trip of latency never makes the pull overshoot.
     * 拉拽开始时设置的持有者双脚终点，以相对钩爪的偏移存储（即使地图坐标很远，浮点数在钩爪附近也保持精确）。持有者客户端
     * 在本地朝它转向（`client/ninja/NinjaGrappleClientPull`），因此网络往返延迟不会让拉拽冲过头。
     */
    private static final TrackedData<Vector3f> PULL_TARGET =
            DataTracker.registerData(NinjaGrapplingHookEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    /** How far the hook point may sit outside the latched block's shape. / 钩点允许偏出被钩方块形状的距离。 */
    private static final double GRIP_TOLERANCE = 0.05;
    private static final State[] STATES = State.values();
    private static final double RANGE_EPSILON = 1.0E-6;
    /** The chain stays visible this far from the viewer (64 blocks). / 观察者在此距离内（64 格）都能看到铁链。 */
    private static final double RENDER_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final float PULL_SOUND_PITCH = 1.4F;

    private @Nullable UUID ownerUuid;
    private int ownerEntityId;
    private double travelled;
    private Direction latchSide = Direction.UP;
    private BlockPos latchBlock = BlockPos.ORIGIN;
    private Vec3d feetTarget = Vec3d.ZERO;
    private int stateTicks;
    private double bestDistance;
    private int ticksWithoutProgress;
    private boolean ended;

    public NinjaGrapplingHookEntity(EntityType<? extends NinjaGrapplingHookEntity> type, World world) {
        super(type, world);
    }

    /** Server throw: starts at the owner's eyes and flies along their look. / 服务端投掷：从持有者眼睛出发，沿视线飞行。 */
    public NinjaGrapplingHookEntity(World world, PlayerEntity owner) {
        this(SparkWitchEntities.ninjaGrapplingHook(), world);
        ownerUuid = owner.getUuid();
        ownerEntityId = owner.getId();
        Vec3d eye = owner.getEyePos();
        Vec3d velocity = owner.getRotationVec(1.0F).multiply(NinjaRules.GRAPPLING_HOOK_FLIGHT_SPEED);
        refreshPositionAndAngles(eye.x, eye.y, eye.z, yawOf(velocity), pitchOf(velocity));
        setVelocity(velocity);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE, (byte) State.FLYING.ordinal());
        builder.add(PULL_TARGET, new Vector3f());
    }

    public State getState() {
        int raw = dataTracker.get(STATE);
        return raw >= 0 && raw < STATES.length ? STATES[raw] : State.FLYING;
    }

    private void setState(State state) {
        dataTracker.set(STATE, (byte) state.ordinal());
        stateTicks = 0;
    }

    /** The synced feet target of a PULLING hook. / 拉拽中钩爪同步的双脚终点。 */
    public Vec3d getPullTarget() {
        return getPos().add(new Vec3d(dataTracker.get(PULL_TARGET)));
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * The thrower on both sides: the server resolves the UUID, a client the entity id from the spawn packet.
     * 双端都可取得投掷者：服务端按 UUID 解析，客户端按生成包中的实体 id 解析。
     */
    public @Nullable PlayerEntity getOwnerPlayer() {
        if (!getWorld().isClient()) {
            return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
        }
        return getWorld().getEntityById(ownerEntityId) instanceof PlayerEntity player ? player : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) {
            // Client prediction only: a flying head advances by its velocity, the server's position updates correct it.
            // 仅客户端预测：飞行中的钩头按速度前进，由服务端位置更新校正。
            if (getState() == State.FLYING) {
                setPosition(getPos().add(getVelocity()));
            }
            return;
        }
        if (isRemoved()) {
            return;
        }
        if (!(getOwnerPlayer() instanceof ServerPlayerEntity owner)) {
            // The owner left the round or the world: nothing to cool down. / 持有者已离开对局或世界：无需冷却。
            ended = true;
            discard();
            return;
        }
        if (!mayKeepChain(owner)) {
            end(owner, true);
            return;
        }
        stateTicks++;
        switch (getState()) {
            case FLYING -> tickFlight(owner);
            case LATCHED -> {
                if (NinjaRules.isGrappleLatchExpired(stateTicks) || !gripHolds()) {
                    end(owner, false);
                }
            }
            case PULLING -> {
                if (gripHolds()) {
                    tickPull(owner);
                } else {
                    end(owner, false);
                }
            }
        }
    }

    /**
     * Chain-break rule: the owner must still pass the throw gate ({@link NinjaGrappleService#mayGrapple}: a match
     * participant stays playing, alive and in survival, anyone else alive and not a spectator), stay within range, not
     * be held by another authority ({@link NinjaGrappleService#isHeld}), and keep the hook in either hand.
     * 断链规则：持有者必须仍满足投掷门槛（{@link NinjaGrappleService#mayGrapple}：对局参与者须在局、存活且为生存模式，其他人
     * 须存活且不是旁观者），处于距离内，未被其他机制持有（{@link NinjaGrappleService#isHeld}），并在任一手中握着钩爪。
     */
    private boolean mayKeepChain(ServerPlayerEntity owner) {
        return !owner.isRemoved()
                && owner.getWorld() == getWorld()
                && NinjaGrappleService.mayGrapple(owner)
                && !NinjaGrappleService.isHeld(owner)
                && NinjaGrappleService.holdsHook(owner)
                && !NinjaRules.isGrappleChainOverstretched(owner.distanceTo(this));
    }

    private void tickFlight(ServerPlayerEntity owner) {
        Vec3d start = getPos();
        Vec3d step = getVelocity();
        double remaining = NinjaRules.GRAPPLING_HOOK_RANGE - travelled;
        double stepLength = step.length();
        if (stepLength > remaining) {
            step = step.multiply(remaining / stepLength);
        }
        Vec3d end = start.add(step);
        // Blocks only, through the hook's own collision shapes; entities never stop it.
        // 只检测方块，使用钩头自身的碰撞形状；实体永远挡不住它。
        BlockHitResult hit = getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (isMapWall(getWorld().getBlockState(hit.getBlockPos()))) {
                // Invisible map walls (vanilla barrier, Wathe barrier panel and light barrier) are a miss.
                // 地图隐形墙（原版屏障、Wathe 屏障板与光照屏障）算作落空。
                setPosition(hit.getPos());
                end(owner, false);
                return;
            }
            latch(owner, hit);
            return;
        }
        setPosition(end);
        travelled += step.length();
        if (travelled >= NinjaRules.GRAPPLING_HOOK_RANGE - RANGE_EPSILON) {
            end(owner, false);
        }
    }

    private void latch(ServerPlayerEntity owner, BlockHitResult hit) {
        Vec3d point = hit.getPos();
        setPosition(point);
        setVelocity(Vec3d.ZERO);
        latchSide = hit.getSide();
        latchBlock = hit.getBlockPos();
        feetTarget = NinjaRules.grappleFeetTarget(point, latchSide, owner.getHeight());
        setState(State.LATCHED);
        playSoundHere(SoundEvents.BLOCK_CHAIN_PLACE, 1.0F);
    }

    /**
     * Second right-click on a latched hook (server only). The target is recomputed for the owner's current height.
     * 对已钩住的钩爪再次右键（仅服务端）。终点按持有者当前身高重新计算。
     */
    public boolean startPull(ServerPlayerEntity owner) {
        if (ended || isRemoved() || getState() != State.LATCHED || !owner.getUuid().equals(ownerUuid)) {
            return false;
        }
        feetTarget = NinjaRules.grappleFeetTarget(getPos(), latchSide, owner.getHeight());
        dataTracker.set(PULL_TARGET, feetTarget.subtract(getPos()).toVector3f());
        bestDistance = owner.getPos().distanceTo(feetTarget);
        ticksWithoutProgress = 0;
        setState(State.PULLING);
        getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.PLAYERS, 1.0F, PULL_SOUND_PITCH);
        return true;
    }

    /**
     * The server owns the pull's start and end but not its steering: the owner's client sets its own velocity toward
     * the synced target each tick (like any vanilla player movement), so walls and doors stop it through ordinary
     * client physics and latency cannot overshoot; nothing is ever teleported. Here the server only judges arrival,
     * stall and timeout from the reported position and keeps fall distance at zero.
     * 服务端掌握拉拽的开始与结束，但不负责转向：持有者客户端每刻朝同步的终点设置自身速度（与原版玩家移动一样），因此墙与门
     * 通过普通客户端物理挡住它，延迟也不会造成冲过头；从不传送。服务端这里只根据上报位置判断到达、卡住与超时，并保持摔落
     * 距离为零。
     */
    private void tickPull(ServerPlayerEntity owner) {
        Vec3d toTarget = feetTarget.subtract(owner.getPos());
        double distance = toTarget.length();
        if (NinjaRules.isGrappleProgress(bestDistance, distance)) {
            bestDistance = distance;
            ticksWithoutProgress = 0;
        } else {
            ticksWithoutProgress++;
        }
        if (NinjaRules.hasGrappleArrived(distance)
                || NinjaRules.isGrapplePullTimedOut(stateTicks)
                || NinjaRules.isGrapplePullStalled(stateTicks, ticksWithoutProgress)) {
            end(owner, false);
            return;
        }
        owner.fallDistance = 0.0F;
    }

    /**
     * The latched block must still hold the hook: an opened door or a removed block drops it.
     * 钩住的方块必须仍能挂住钩爪：门被打开或方块被移除时钩爪脱落。
     */
    private boolean gripHolds() {
        BlockState state = getWorld().getBlockState(latchBlock);
        Vec3d point = getPos();
        for (Box box : state.getCollisionShape(getWorld(), latchBlock, ShapeContext.of(this)).getBoundingBoxes()) {
            if (box.offset(latchBlock).expand(GRIP_TOLERANCE).contains(point)) {
                return true;
            }
        }
        return false;
    }

    /** Vanilla and Wathe invisible map walls. / 原版与 Wathe 的隐形地图墙。 */
    private static boolean isMapWall(BlockState state) {
        return state.isOf(Blocks.BARRIER) || state.isOf(WatheBlocks.BARRIER_PANEL) || state.isOf(WatheBlocks.LIGHT_BARRIER);
    }

    /**
     * Ends a cycle (pull done, miss, latch timeout or chain break): stops a pull, plays the chain sound, starts the
     * item cooldown and removes the hook.
     * 结束一次循环（拉拽完成、落空、钩住超时或断链）：停止拉拽、播放铁链声、开始物品冷却并移除钩爪。
     */
    private void end(ServerPlayerEntity owner, boolean broken) {
        if (ended) {
            return;
        }
        ended = true;
        if (getState() == State.PULLING) {
            stopPull(owner);
        }
        playSoundHere(broken ? SoundEvents.BLOCK_CHAIN_BREAK : SoundEvents.BLOCK_CHAIN_FALL, 1.0F);
        // Never shorten a longer cooldown another feature forced while the hook was out (Fiend aura, Karma, AC shell).
        // 绝不缩短钩爪在外期间其他机制强加的更长冷却（魔人光环、业报、AC 弹）。
        if (ForcedCooldowns.itemRemainingTicks(owner, SparkWitchItems.ninjaGrapplingHook())
                < NinjaRules.GRAPPLING_HOOK_COOLDOWN_TICKS) {
            owner.getItemCooldownManager().set(
                    SparkWitchItems.ninjaGrapplingHook(),
                    NinjaRules.GRAPPLING_HOOK_COOLDOWN_TICKS
            );
        }
        discard();
    }

    /**
     * Death, reset, role change and round-end cleanup: no sound and no cooldown, but a running pull still stops.
     * 死亡、重置、职业变更与回合结束清理：无音效、无冷却，但进行中的拉拽仍会停止。
     */
    public void discardSilently() {
        if (!ended && getState() == State.PULLING && getOwnerPlayer() instanceof ServerPlayerEntity owner) {
            stopPull(owner);
        }
        ended = true;
        discard();
    }

    private static void stopPull(ServerPlayerEntity owner) {
        owner.setVelocity(Vec3d.ZERO);
        owner.velocityModified = true;
        owner.fallDistance = 0.0F;
    }

    private void playSoundHere(SoundEvent sound, float pitch) {
        getWorld().playSound(null, getX(), getY(), getZ(), sound, SoundCategory.PLAYERS, 1.0F, pitch);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!getWorld().isClient()) {
            NinjaGrappleService.forget(this);
        }
    }

    /** Owner id in the spawn packet, as vanilla projectiles do. / 与原版投射物一样在生成包中携带持有者 id。 */
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        PlayerEntity owner = getOwnerPlayer();
        return new EntitySpawnS2CPacket(this, entry, owner == null ? 0 : owner.getId());
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        ownerEntityId = packet.getEntityData();
        setVelocity(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    /** Never a crosshair, attack or projectile target. / 永远不会成为准星、攻击或投射物的目标。 */
    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < RENDER_DISTANCE_SQUARED;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    private static float yawOf(Vec3d velocity) {
        return (float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN);
    }

    private static float pitchOf(Vec3d velocity) {
        return (float) (MathHelper.atan2(velocity.y, velocity.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
    }
}
