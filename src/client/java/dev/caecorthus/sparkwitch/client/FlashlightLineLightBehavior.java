package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.impl.FlashlightLineLightRules;
import dev.caecorthus.sparkwitch.item.FlashlightItem;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Moonlight-lamp style directional light that follows a lit held flashlight.
 * 跟随已开启手电筒的月光灯风格方向性光源。
 */
public final class FlashlightLineLightBehavior implements DynamicLightBehavior {
    private static final double BOUNDING_MARGIN_BLOCKS = 8.0;
    private static final float ROTATION_EPSILON_DEGREES = 0.2F;
    private static final double POSITION_EPSILON_BLOCKS = 0.05;

    private final PlayerEntity player;
    private float lastYaw;
    private float lastPitch;
    private Vec3d lastPos;
    private Vec3d eyePos;
    private Vec3d direction;

    public FlashlightLineLightBehavior(PlayerEntity player) {
        this.player = player;
        this.lastYaw = player.getYaw();
        this.lastPitch = player.getPitch();
        this.lastPos = player.getPos();
        updateSnapshot();
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        if (!FlashlightItem.isHeldOn(player)) {
            return 0.0;
        }
        double light = FlashlightLineLightRules.lightAt(
                eyePos.x,
                eyePos.y,
                eyePos.z,
                direction.x,
                direction.y,
                direction.z,
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
        if (light <= 0.0 || isOccluded(pos)) {
            return 0.0;
        }
        return light;
    }

    @Override
    public BoundingBox getBoundingBox() {
        Vec3d end = eyePos.add(direction.multiply(FlashlightLineLightRules.RANGE_BLOCKS));
        int startX = MathHelper.floor(Math.min(eyePos.x, end.x) - BOUNDING_MARGIN_BLOCKS);
        int startY = MathHelper.floor(Math.min(eyePos.y, end.y) - BOUNDING_MARGIN_BLOCKS);
        int startZ = MathHelper.floor(Math.min(eyePos.z, end.z) - BOUNDING_MARGIN_BLOCKS);
        int endX = MathHelper.floor(Math.max(eyePos.x, end.x) + BOUNDING_MARGIN_BLOCKS);
        int endY = MathHelper.floor(Math.max(eyePos.y, end.y) + BOUNDING_MARGIN_BLOCKS);
        int endZ = MathHelper.floor(Math.max(eyePos.z, end.z) + BOUNDING_MARGIN_BLOCKS);
        return new BoundingBox(startX, startY, startZ, endX, endY, endZ);
    }

    @Override
    public boolean hasChanged() {
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d pos = player.getPos();
        boolean changed = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw)) > ROTATION_EPSILON_DEGREES
                || Math.abs(pitch - lastPitch) > ROTATION_EPSILON_DEGREES
                || pos.distanceTo(lastPos) > POSITION_EPSILON_BLOCKS;
        if (!changed) {
            return false;
        }

        lastYaw = yaw;
        lastPitch = pitch;
        lastPos = pos;
        updateSnapshot();
        return true;
    }

    @Override
    public boolean isRemoved() {
        return player.isRemoved() || !FlashlightItem.isHeldOn(player);
    }

    private void updateSnapshot() {
        eyePos = player.getEyePos();
        direction = player.getRotationVector().normalize();
    }

    private boolean isOccluded(BlockPos pos) {
        // Match a handheld beam: blocks behind a wall should not receive the cone light.
        // 模拟手持光束：墙后的方块不应获得锥形光照。
        HitResult hit = player.getWorld().raycast(new RaycastContext(
                eyePos,
                Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.BLOCK
                && hit instanceof net.minecraft.util.hit.BlockHitResult blockHit
                && !blockHit.getBlockPos().equals(pos);
    }
}
