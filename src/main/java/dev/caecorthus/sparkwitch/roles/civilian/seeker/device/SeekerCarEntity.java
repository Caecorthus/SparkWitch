package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Search Car ({@code sparkwitch:seeker_car}); movement delegates to {@code SeekerCarMovement} (WP-09): the server
 * tick calls {@code SeekerCarMovement.serverIdleTick(this)} every tick (WP-09 no-ops while driven),
 * {@code isLogicalSideForUpdatingMovement} returns {@code SeekerCarMovement.isLogicalSide(this)}, and while
 * {@code clientDriveHook().isLocallyDriven(this)} the client ignores tracker position/velocity updates because the
 * WP-10a driver positions the entity. Other clients interpolate tracker updates boat-style over three steps.
 * Interaction: owner outside a session → {@code SeekerDeviceService.recallCar}. Idle cars step off corpses and break
 * (VOID) when they fall below the play area; only driven cars emit the throttled motor sound.
 * 搜寻小车（{@code sparkwitch:seeker_car}）；移动逻辑委托给 {@code SeekerCarMovement}（WP-09）：服务端每刻调用
 * {@code serverIdleTick}（驾驶中由 WP-09 空操作），逻辑端判定委托 {@code isLogicalSide}；本地驾驶时客户端忽略追踪包的
 * 位置与速度，由 WP-10a 驾驶器定位实体；其他客户端按船式三步插值。交互：拥有者在会话外右键 →
 * {@code SeekerDeviceService.recallCar}。空闲小车会让开尸体，掉到游戏区域下方时按 VOID 损坏；只有被驾驶的小车
 * 才会发出节流的电机声。
 */
public class SeekerCarEntity extends SeekerDeviceEntity {
    private static final int INTERPOLATION_STEPS = 3;

    private int lastMotorSoundAge = Integer.MIN_VALUE / 2;
    private int lerpTicks;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;

    public SeekerCarEntity(EntityType<? extends SeekerCarEntity> type, World world) {
        super(type, world);
    }

    @Override
    public SeekerDeviceKind kind() {
        return SeekerDeviceKind.CAR;
    }

    @Override
    public double targetingMargin() {
        return SeekerRules.CAR_TARGET_MARGIN;
    }

    /**
     * Frozen hook: the server calls this each time it accepts a driven move; the entity throttles the motor sound
     * ({@link SeekerRules#MOTOR_SOUND_INTERVAL_TICKS}). Idle cars stay silent.
     * 冻结钩子：服务端每接受一次驾驶移动就调用一次；由实体按间隔节流电机声。静止的小车保持安静。
     */
    public void onDrivenMove() {
        if (getWorld().isClient() || isRemoved()) {
            return;
        }
        if (age - lastMotorSoundAge >= SeekerRules.MOTOR_SOUND_INTERVAL_TICKS) {
            lastMotorSoundAge = age;
            SeekerDeviceSounds.playMotor(this);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (getWorld().isClient()) {
            clientInterpolate();
            return;
        }
        SeekerCarMovement.serverIdleTick(this);
        if (isRemoved() || !(getWorld() instanceof ServerWorld world)) {
            return;
        }
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        if (SeekerPlacementRules.isBelowPlayArea(getY(), playArea)) {
            // Fell out of the train: 180 s, never marks. Idempotent if WP-09's checks got there first.
            // 掉出列车：180 秒，不标记。若 WP-09 的检查已先处理则幂等。
            SeekerDeviceService.breakDevice(this, SeekerBreakSource.VOID, null);
            return;
        }
        if (age % SeekerPlacementRules.BODY_PUSH_INTERVAL_TICKS == 0 && !isDriven()) {
            pushOffBodies(world, playArea);
        }
    }

    /** Server: the owner is remote-controlling this car right now. / 服务端：拥有者此刻正在遥控这辆小车。 */
    private boolean isDriven() {
        ServerPlayerEntity owner = SeekerDeviceService.findOwner(this);
        if (owner == null) {
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        return status != null && status.sessionMode() == SeekerSessionMode.CAR && status.carEntityId() == getId();
    }

    /** Keeps an idle car from covering a corpse (body bag, inspection). / 防止空闲小车挡住尸体（装袋、检查）。 */
    private void pushOffBodies(ServerWorld world, Box playArea) {
        List<PlayerBodyEntity> bodies = world.getEntitiesByClass(PlayerBodyEntity.class, getBoundingBox(),
                body -> body.isAlive() && !body.isRemoved());
        if (bodies.isEmpty()) {
            return;
        }
        Vec3d direction = SeekerPlacementRules.pushOffDirection(getPos(), bodies.getFirst().getPos(), getYaw());
        Vec3d target = getPos().add(direction.multiply(SeekerPlacementRules.BODY_PUSH_DISTANCE));
        if (SeekerPlacementRules.withinPlayArea(playArea, target)
                && world.isSpaceEmpty(this, SeekerPlacementRules.carClearanceBox(target))
                && !world.isSpaceEmpty(this, SeekerPlacementRules.carSupportProbe(target))) {
            setPosition(target);
        }
    }

    // ---- Interaction ----

    /**
     * Server: the owner outside a session recalls the car (vanilla reach already validated the interaction).
     * Client: SUCCESS only for the local owner (read from the owner-only component), PASS for everyone else.
     * 服务端：会话外的拥有者右键回收（原版已校验交互距离）。客户端：仅本地拥有者（读取仅拥有者组件）返回 SUCCESS，其他人 PASS。
     */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient()) {
            SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
            return status != null && status.carEntityId() == getId()
                    && status.sessionMode() == SeekerSessionMode.NONE ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        if (player instanceof ServerPlayerEntity owner && isOwnedBy(owner.getUuid())) {
            return SeekerDeviceService.recallCar(owner, this);
        }
        return ActionResult.PASS;
    }

    // ---- Movement delegation (WP-09) and client interpolation ----

    @Override
    public boolean isLogicalSideForUpdatingMovement() {
        return SeekerCarMovement.isLogicalSide(this);
    }

    /**
     * Client only: true while the local WP-10a driver positions this car. The hook is consulted only on the client,
     * because an integrated server shares the JVM (and entity ids) with the client that installed it.
     * 仅客户端：本地 WP-10a 驾驶器正在定位这辆小车时为 true。只在客户端查询钩子，因为集成服务端与安装钩子的客户端
     * 共享同一 JVM（以及实体 id）。
     */
    private boolean isLocallyDriven() {
        if (!getWorld().isClient()) {
            return false;
        }
        SeekerCarMovement.ClientDriveHook hook = SeekerCarMovement.clientDriveHook();
        return hook != null && hook.isLocallyDriven(this);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                               int interpolationSteps) {
        if (isLocallyDriven()) {
            lerpTicks = 0;
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpTicks = INTERPOLATION_STEPS;
    }

    @Override
    public void setVelocityClient(double x, double y, double z) {
        if (!isLocallyDriven()) {
            super.setVelocityClient(x, y, z);
        }
    }

    private void clientInterpolate() {
        if (isLocallyDriven()) {
            lerpTicks = 0;
            return;
        }
        if (lerpTicks > 0) {
            lerpPosAndRotation(lerpTicks, lerpX, lerpY, lerpZ, lerpYaw, getPitch());
            lerpTicks--;
        }
    }

    @Override
    public double getLerpTargetX() {
        return lerpTicks > 0 ? lerpX : getX();
    }

    @Override
    public double getLerpTargetY() {
        return lerpTicks > 0 ? lerpY : getY();
    }

    @Override
    public double getLerpTargetZ() {
        return lerpTicks > 0 ? lerpZ : getZ();
    }

    @Override
    public float getLerpTargetYaw() {
        return lerpTicks > 0 ? lerpYaw : getYaw();
    }
}
