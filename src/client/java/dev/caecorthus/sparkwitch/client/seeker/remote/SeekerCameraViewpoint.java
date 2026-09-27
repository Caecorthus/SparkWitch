package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Client-only camera viewpoint used as the render camera in CAMERA mode. It is never added to the world (so it is
 * never ticked, tracked, saved or rendered) and borrows the zero-size marker type, whose eye height is 0, so the
 * render camera sits exactly at {@link #getPos()}: the camera box centre plus facing x
 * {@link SeekerRules#CAMERA_VIEW_OFFSET}. Mouse look is clamped to the mount cone; {@code prev*} fields are set by
 * hand every tick because nothing else ticks this entity. Purely cosmetic: the server never sees its rotation.
 * CAMERA 模式下作为渲染相机的纯客户端摄像头视点。它从不加入世界（因此不会被 tick、追踪、保存或渲染），
 * 借用零尺寸的标记实体类型（眼高为 0），渲染相机恰好位于 {@link #getPos()}：摄像头碰撞箱中心加朝向 ×
 * {@link SeekerRules#CAMERA_VIEW_OFFSET}。鼠标视角被钳制在安装锥角内；由于没有其他逻辑 tick 该实体，
 * {@code prev*} 字段每刻手动设置。纯外观：服务端从不知道其朝向。
 */
public final class SeekerCameraViewpoint extends Entity {
    private final SeekerCameraEntity mount;
    private Direction facing;
    private float mountYaw;

    public SeekerCameraViewpoint(World world, SeekerCameraEntity mount) {
        super(EntityType.MARKER, world);
        this.mount = mount;
        this.noClip = true;
        this.facing = mount.facing();
        this.mountYaw = mount.mountYaw();
        recenter();
        followMount();
    }

    /** The placed camera this viewpoint looks through. / 该视点所依附的已放置摄像头。 */
    public SeekerCameraEntity mount() {
        return mount;
    }

    /**
     * Once per client tick: settle interpolation, follow the mount and re-centre if its orientation changed.
     * 每个客户端刻调用一次：结算插值、跟随摄像头，并在其朝向变化时重新居中。
     */
    public void followMount() {
        Direction currentFacing = mount.facing();
        float currentMountYaw = mount.mountYaw();
        if (currentFacing != facing || currentMountYaw != mountYaw) {
            facing = currentFacing;
            mountYaw = currentMountYaw;
            recenter();
        }
        Vec3d lens = mount.getBoundingBox().getCenter().add(
                facing.getOffsetX() * SeekerRules.CAMERA_VIEW_OFFSET,
                facing.getOffsetY() * SeekerRules.CAMERA_VIEW_OFFSET,
                facing.getOffsetZ() * SeekerRules.CAMERA_VIEW_OFFSET);
        setPosition(lens);
        prevX = lens.x;
        prevY = lens.y;
        prevZ = lens.z;
        lastRenderX = lens.x;
        lastRenderY = lens.y;
        lastRenderZ = lens.z;
        prevYaw = getYaw();
        prevPitch = getPitch();
    }

    /**
     * Applies one mouse delta (already sensitivity-scaled by vanilla) inside the cone; the {@code prev*} fields move
     * by the applied amount so frame interpolation stays smooth.
     * 在锥角内应用一次鼠标增量（原版已按灵敏度缩放）；{@code prev*} 字段按实际应用量同步移动，保证帧间插值平滑。
     */
    public void look(double cursorDeltaX, double cursorDeltaY) {
        float yawCenter = SeekerRemoteViewRules.coneYawCenter(facing, mountYaw);
        float pitchCenter = SeekerRemoteViewRules.conePitchCenter(facing);
        float oldYaw = getYaw();
        float oldPitch = getPitch();
        float yaw = SeekerRemoteViewRules.clampConeYaw(
                oldYaw + (float) cursorDeltaX * SeekerRemoteViewRules.LOOK_SCALE, yawCenter);
        float pitch = SeekerRemoteViewRules.clampConePitch(
                oldPitch + (float) cursorDeltaY * SeekerRemoteViewRules.LOOK_SCALE, pitchCenter);
        setYaw(yaw);
        setPitch(pitch);
        prevYaw += yaw - oldYaw;
        prevPitch += pitch - oldPitch;
    }

    private void recenter() {
        setYaw(SeekerRemoteViewRules.coneYawCenter(facing, mountYaw));
        setPitch(SeekerRemoteViewRules.conePitchCenter(facing));
        prevYaw = getYaw();
        prevPitch = getPitch();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
