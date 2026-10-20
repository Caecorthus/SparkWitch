package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Where a player's Mini Wither hovers, shared by the server (skull origin, ambient sound) and the client (rendering) so
 * both agree. The hover point is pulled in front of any wall between the eye and it, so the Wither never sits inside a
 * wall nor fires from behind one.
 * 玩家小凋灵的悬浮位置，服务端（头颅发射点、环境音）与客户端（渲染）共用，因此两端一致。眼睛到悬浮点之间有墙时悬浮点会被
 * 拉到墙前，小凋灵因此既不会嵌进墙里，也不会隔墙开火。
 */
public final class MiniWitherAnchor {
    private MiniWitherAnchor() {
    }

    /** The current position (server ticks, partial tick 1). / 当前位置（服务端刻，部分刻为 1）。 */
    public static Vec3d position(PlayerEntity player) {
        return position(player, 1.0F);
    }

    /**
     * Interpolated like {@code WorldRenderer#renderEntity}, so the client draw stays glued to the player.
     * 与 {@code WorldRenderer#renderEntity} 相同的插值，客户端绘制因此紧贴玩家。
     */
    public static Vec3d position(PlayerEntity player, float tickDelta) {
        Vec3d feet = new Vec3d(
                MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()),
                MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()),
                MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()));
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        double eyeHeight = player.getStandingEyeHeight();
        Vec3d local = MiniWitherRules.localAnchor(player.getMainArm() == Arm.RIGHT, eyeHeight,
                player.age + tickDelta);
        return clampFromEye(player, feet.add(0.0, eyeHeight, 0.0),
                feet.add(MiniWitherRules.toWorldOffset(local, bodyYaw)));
    }

    /**
     * Pulls {@code point} in front of the first wall between {@code eye} and it; unchanged when the view is clear.
     * 把 {@code point} 拉到 {@code eye} 与它之间第一堵墙的前方；视线无遮挡时不变。
     */
    public static Vec3d clampFromEye(PlayerEntity player, Vec3d eye, Vec3d point) {
        BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, point,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return point;
        }
        Vec3d segment = point.subtract(eye);
        double fraction = MiniWitherRules.clampFraction(segment.length(), eye.distanceTo(hit.getPos()));
        return eye.add(segment.multiply(fraction));
    }

    /** Body yaw for the Wither's facing. / 小凋灵朝向使用的身体偏航。 */
    public static float bodyYaw(PlayerEntity player, float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
    }
}
