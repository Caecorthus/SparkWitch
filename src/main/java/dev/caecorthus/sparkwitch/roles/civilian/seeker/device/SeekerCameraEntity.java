package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Security Camera ({@code sparkwitch:seeker_camera}); static, no gravity, oriented by FACING (the mount face
 * normal) and MOUNT_YAW (horizontal view centre), both client-visible through the DataTracker because they are pure
 * appearance. The server breaks it (environment source, never marks) once its mount face is no longer a full solid
 * face.
 * 摄像头（{@code sparkwitch:seeker_camera}）；静止、无重力，按 FACING（依附面法线）与 MOUNT_YAW（水平视野中心）定向，
 * 两者都是纯外观，因此经 DataTracker 对客户端可见。依附面不再是完整实心面时，服务端将其按环境来源损坏（从不标记）。
 */
public class SeekerCameraEntity extends SeekerDeviceEntity {
    private static final TrackedData<Direction> FACING =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Float> MOUNT_YAW =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public SeekerCameraEntity(EntityType<? extends SeekerCameraEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(FACING, Direction.UP);
        builder.add(MOUNT_YAW, 0.0F);
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
        return dataTracker.get(FACING);
    }

    /** Horizontal centre of floor/ceiling cameras (client-visible). / 地面/天花板摄像头的水平中心（客户端可见）。 */
    public float mountYaw() {
        return dataTracker.get(MOUNT_YAW);
    }

    /** Server only; called once at placement. / 仅服务端；放置时调用一次。 */
    public void setMount(Direction facing, float mountYaw) {
        float yaw = MathHelper.wrapDegrees(mountYaw);
        dataTracker.set(FACING, facing);
        dataTracker.set(MOUNT_YAW, yaw);
        setYaw(yaw);
        setHeadYaw(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || !(getWorld() instanceof ServerWorld world)) {
            return;
        }
        setVelocity(Vec3d.ZERO);
        if (age % SeekerPlacementRules.CAMERA_SUPPORT_CHECK_INTERVAL_TICKS == 0 && !hasSolidMount(world)) {
            SeekerDeviceService.breakDevice(this, SeekerBreakSource.VOID, null);
        }
    }

    private boolean hasSolidMount(ServerWorld world) {
        Direction facing = facing();
        BlockPos support = SeekerPlacementRules.cameraSupportPos(getPos(), facing);
        BlockState state = world.getBlockState(support);
        return Block.isFaceFullSquare(state.getCollisionShape(world, support), facing);
    }
}
