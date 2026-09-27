package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Security Camera ({@code sparkwitch:seeker_camera}); static, no gravity, oriented by FACING and MOUNT_YAW.
 * TODO(WP-03): implement the camera entity and its DataTracker fields. / 待 WP-03 实现摄像头实体及其 DataTracker 字段。
 * 摄像头（{@code sparkwitch:seeker_camera}）；静止、无重力，按 FACING 与 MOUNT_YAW 定向。
 */
public class SeekerCameraEntity extends SeekerDeviceEntity {
    public SeekerCameraEntity(EntityType<? extends SeekerCameraEntity> type, World world) {
        super(type, world);
    }

    @Override
    public SeekerDeviceKind kind() {
        return SeekerDeviceKind.CAMERA;
    }

    @Override
    public double targetingMargin() {
        return SeekerRules.CAMERA_TARGET_MARGIN;
    }

    /** Surface normal the camera is mounted on (client-visible). / 摄像头依附面的法线（客户端可见）。 */
    public Direction facing() {
        return Direction.UP;
    }

    /** Horizontal centre of floor/ceiling cameras (client-visible). / 地面/天花板摄像头的水平中心（客户端可见）。 */
    public float mountYaw() {
        return 0.0F;
    }

    /** Server only; called once at placement. / 仅服务端；放置时调用一次。 */
    public void setMount(Direction facing, float mountYaw) {
        // TODO(WP-03) / 待 WP-03 实现
    }
}
