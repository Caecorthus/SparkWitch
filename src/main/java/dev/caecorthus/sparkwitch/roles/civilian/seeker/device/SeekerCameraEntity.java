package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The Security Camera ({@code sparkwitch:seeker_camera}); static, no gravity, oriented by FACING (the mount face
 * normal) and MOUNT_YAW (horizontal view centre), both client-visible through the DataTracker because they are pure
 * appearance. The server breaks it (environment source, never marks) once its mount face is no longer a full solid
 * face.
 * <p>
 * Public look state (every tracking client sees it; it never names the owner): LOOK_YAW and LOOK_PITCH are the
 * direction the head points, set to the cone centre at placement and afterwards only by the server from the owner's
 * clamped {@code seeker_camera_look}; the head holds it when nobody views. VIEWING is true while the owner's session
 * views this camera; the server derives it every tick from the owner's component and the client only lights the LED.
 * 摄像头（{@code sparkwitch:seeker_camera}）；静止、无重力，按 FACING（依附面法线）与 MOUNT_YAW（水平视野中心）定向，
 * 两者都是纯外观，因此经 DataTracker 对客户端可见。依附面不再是完整实心面时，服务端将其按环境来源损坏（从不标记）。
 * 公开的视角状态（所有追踪客户端可见，但从不透露拥有者）：LOOK_YAW 与 LOOK_PITCH 是机头朝向，放置时为锥角中心，
 * 之后仅由服务端根据拥有者经钳制的 {@code seeker_camera_look} 设置；无人观看时机头保持该朝向。VIEWING 在拥有者的会话
 * 正在观看本摄像头时为 true；由服务端每刻根据拥有者组件推导，客户端只据此点亮指示灯。
 */
public class SeekerCameraEntity extends SeekerDeviceEntity {
    private static final TrackedData<Direction> FACING =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Float> MOUNT_YAW =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LOOK_YAW =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LOOK_PITCH =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> VIEWING =
            DataTracker.registerData(SeekerCameraEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // Client-only render smoothing of the synced look; never synced or saved.
    // 仅客户端：对同步视角的渲染平滑；从不同步也不存盘。
    private boolean renderLookReady;
    private float renderLookYaw;
    private float prevRenderLookYaw;
    private float renderLookPitch;
    private float prevRenderLookPitch;

    public SeekerCameraEntity(EntityType<? extends SeekerCameraEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(FACING, Direction.UP);
        builder.add(MOUNT_YAW, 0.0F);
        builder.add(LOOK_YAW, 0.0F);
        builder.add(LOOK_PITCH, 0.0F);
        builder.add(VIEWING, false);
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

    /** Synced head yaw, wrapped and inside the cone (client-visible). / 同步的机头偏航，已规范且位于锥角内（客户端可见）。 */
    public float lookYaw() {
        return dataTracker.get(LOOK_YAW);
    }

    /** Synced head pitch inside the cone (client-visible). / 同步的机头俯仰，位于锥角内（客户端可见）。 */
    public float lookPitch() {
        return dataTracker.get(LOOK_PITCH);
    }

    /** True while the owner views through this camera (client-visible). / 拥有者正通过本摄像头观看时为 true（客户端可见）。 */
    public boolean isViewed() {
        return dataTracker.get(VIEWING);
    }

    /**
     * Server only; called once at placement. The head starts at the cone centre the owner's view starts from.
     * 仅服务端；放置时调用一次。机头初始朝向为锥角中心，与拥有者视角的初始朝向一致。
     */
    public void setMount(Direction facing, float mountYaw) {
        float yaw = MathHelper.wrapDegrees(mountYaw);
        dataTracker.set(FACING, facing);
        dataTracker.set(MOUNT_YAW, yaw);
        dataTracker.set(LOOK_YAW, MathHelper.wrapDegrees(SeekerCameraLookRules.coneYawCenter(facing, yaw)));
        dataTracker.set(LOOK_PITCH, SeekerCameraLookRules.conePitchCenter(facing));
        setYaw(yaw);
        setHeadYaw(yaw);
    }

    /**
     * Server only: points the head at an untrusted look, wrapped and clamped into this mount's cone. Non-finite values
     * are ignored. The DataTracker only resends on change.
     * 仅服务端：把机头指向不可信的视角，规范并钳制到本安装位置的锥角内；非有限值被忽略。DataTracker 仅在变化时重发。
     */
    public void setLook(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        Direction facing = facing();
        dataTracker.set(LOOK_YAW, SeekerCameraLookRules.clampYaw(facing, mountYaw(), yaw));
        dataTracker.set(LOOK_PITCH, SeekerCameraLookRules.clampPitch(facing, pitch));
    }

    /** Client render yaw, smoothed per tick and lerped per frame. / 客户端渲染偏航：逐刻平滑、逐帧插值。 */
    public float renderLookYaw(float tickDelta) {
        ensureRenderLook();
        return MathHelper.lerp(tickDelta, prevRenderLookYaw, renderLookYaw);
    }

    /** Client render pitch, smoothed per tick and lerped per frame. / 客户端渲染俯仰：逐刻平滑、逐帧插值。 */
    public float renderLookPitch(float tickDelta) {
        ensureRenderLook();
        return MathHelper.lerp(tickDelta, prevRenderLookPitch, renderLookPitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (!(getWorld() instanceof ServerWorld world)) {
            tickRenderLook();
            return;
        }
        setVelocity(Vec3d.ZERO);
        dataTracker.set(VIEWING, isViewedByOwner(world));
        if (age % SeekerPlacementRules.CAMERA_SUPPORT_CHECK_INTERVAL_TICKS == 0 && !hasSolidMount(world)) {
            SeekerDeviceService.breakDevice(this, SeekerBreakSource.VOID, null);
        }
    }

    /**
     * Server authority for the LED: the owner's session is CAMERA mode focused on this entity.
     * 指示灯的服务端权威判定：拥有者的会话处于 CAMERA 模式且焦点是本实体。
     */
    private boolean isViewedByOwner(ServerWorld world) {
        UUID owner = ownerUuid();
        ServerPlayerEntity player = owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
        SeekerStatusComponent status = player == null ? null : SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return false;
        }
        SeekerState state = status.state();
        return state.sessionMode() == SeekerSessionMode.CAMERA && state.sessionFocusEntityId() == getId();
    }

    /**
     * Client: the head eases toward the synced look along the short arc; the stored yaw is re-wrapped together with its
     * previous value so the frame lerp stays continuous.
     * 客户端：机头沿最短弧逐渐转向同步视角；存储的 yaw 与其上一刻值一起重新规范，帧插值保持连续。
     */
    private void tickRenderLook() {
        ensureRenderLook();
        prevRenderLookYaw = renderLookYaw;
        prevRenderLookPitch = renderLookPitch;
        renderLookYaw = SeekerCameraLookRules.approachYaw(renderLookYaw, lookYaw());
        renderLookPitch = SeekerCameraLookRules.approachPitch(renderLookPitch, lookPitch());
        float shift = MathHelper.wrapDegrees(renderLookYaw) - renderLookYaw;
        renderLookYaw += shift;
        prevRenderLookYaw += shift;
    }

    /** The spawn bundle carries the look, so the first read snaps to it. / 生成包已携带视角，首次读取直接对齐。 */
    private void ensureRenderLook() {
        if (!renderLookReady) {
            renderLookReady = true;
            renderLookYaw = prevRenderLookYaw = lookYaw();
            renderLookPitch = prevRenderLookPitch = lookPitch();
        }
    }

    private boolean hasSolidMount(ServerWorld world) {
        Direction facing = facing();
        BlockPos support = SeekerPlacementRules.cameraSupportPos(getPos(), facing);
        BlockState state = world.getBlockState(support);
        return Block.isFaceFullSquare(state.getCollisionShape(world, support), facing);
    }
}
