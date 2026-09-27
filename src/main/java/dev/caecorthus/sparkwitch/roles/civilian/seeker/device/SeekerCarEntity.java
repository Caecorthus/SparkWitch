package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

/**
 * The Search Car ({@code sparkwitch:seeker_car}); movement delegates to {@code SeekerCarMovement} (WP-09): the server
 * tick calls {@code SeekerCarMovement.serverIdleTick(this)} every tick (WP-09 no-ops while driven),
 * {@code isLogicalSideForUpdatingMovement} returns {@code SeekerCarMovement.isLogicalSide(this)}, and while
 * {@code clientDriveHook().isLocallyDriven(this)} the client ignores tracker position/velocity updates because the
 * WP-10a driver positions the entity. Interaction: owner outside a session → {@code SeekerDeviceService.recallCar}.
 * TODO(WP-03): implement the car entity. / 待 WP-03 实现小车实体。
 * 搜寻小车（{@code sparkwitch:seeker_car}）；移动逻辑委托给 {@code SeekerCarMovement}（WP-09）：服务端每刻调用
 * {@code serverIdleTick}（驾驶中由 WP-09 空操作），逻辑端判定委托 {@code isLogicalSide}；本地驾驶时客户端忽略追踪包的
 * 位置与速度，由 WP-10a 驾驶器定位实体。交互：拥有者在会话外右键 → {@code SeekerDeviceService.recallCar}。
 */
public class SeekerCarEntity extends SeekerDeviceEntity {
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
        // TODO(WP-03) / 待 WP-03 实现
    }
}
