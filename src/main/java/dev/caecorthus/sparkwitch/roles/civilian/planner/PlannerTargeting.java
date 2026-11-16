package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side targeting for the recommendation and the task slip. Walls count: the aim ray stops at the first block it
 * hits (the PR's {@code ProjectileUtil.raycast} alone went through walls), and a client-named target must be within
 * {@link PlannerRules#REACH} of the eyes (measured to its box) and visible from the eyes at its eyes or its centre.
 * Spectators are transparent and never targets: a Rift Gate occupant is an alive spectator (Orthopedist precedent).
 * 推荐与任务单的服务端目标判定。墙会挡住：瞄准射线止于第一个命中的方块（PR 中单用 {@code ProjectileUtil.raycast} 会穿墙）；
 * 客户端指定的目标必须位于眼睛 {@link PlannerRules#REACH} 格内（量到其碰撞箱），且从眼睛能看到其眼睛或中心。旁观者是透明的，
 * 永远不是目标：裂隙门内的玩家是存活旁观者（沿用正骨师先例）。
 */
final class PlannerTargeting {
    private PlannerTargeting() {
    }

    /**
     * A living, present round participant other than a swallowed player or an active Wraith (whose task map belongs to
     * the Wraith runtime).
     * 在场的存活局内参与者，且不是被吞下的玩家或激活的冤魂（其任务表归冤魂运行时管理）。
     */
    static boolean isTargetable(@Nullable PlayerEntity target) {
        return target != null
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !target.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !SparkWitchApi.isWraithActive(target);
    }

    /** The targetable player under {@code user}'s crosshair within reach, walls included. / 准星下、距离内的可选玩家，墙会阻挡。 */
    static @Nullable ServerPlayerEntity findAimedPlayer(ServerPlayerEntity user) {
        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(PlannerRules.REACH));
        BlockHitResult block = user.getWorld().raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        double reach = start.distanceTo(end);
        Box sweep = user.getBoundingBox().stretch(look.multiply(reach)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(user, start, end, sweep,
                entity -> entity instanceof ServerPlayerEntity other && other != user && isTargetable(other),
                reach * reach);
        return hit != null && hit.getEntity() instanceof ServerPlayerEntity target ? target : null;
    }

    /** Within reach of {@code from}'s eyes, measured to the target's box. / 从 {@code from} 的眼睛到目标碰撞箱在距离内。 */
    static boolean inReach(PlayerEntity from, PlayerEntity target) {
        Vec3d eyes = from.getEyePos();
        Box box = target.getBoundingBox();
        double dx = MathHelper.clamp(eyes.x, box.minX, box.maxX) - eyes.x;
        double dy = MathHelper.clamp(eyes.y, box.minY, box.maxY) - eyes.y;
        double dz = MathHelper.clamp(eyes.z, box.minZ, box.maxZ) - eyes.z;
        return PlannerRules.withinReach(dx * dx + dy * dy + dz * dz);
    }

    /** {@code from}'s eyes see the target's eyes or its centre through no block. / 眼睛与目标的眼睛或中心之间没有方块。 */
    static boolean inSight(PlayerEntity from, PlayerEntity target) {
        Vec3d eyes = from.getEyePos();
        return clear(from, eyes, target.getEyePos()) || clear(from, eyes, target.getBoundingBox().getCenter());
    }

    private static boolean clear(PlayerEntity from, Vec3d start, Vec3d end) {
        return from.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, from)).getType() == HitResult.Type.MISS;
    }
}
