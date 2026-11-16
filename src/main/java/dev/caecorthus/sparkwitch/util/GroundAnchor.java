package dev.caecorthus.sparkwitch.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Shared ground anchor for feign/collapse cycles (Fiend, Ferryman, Hiro victim, Schrödinger's Cat): the player's feet
 * when it stands on the ground, otherwise the floor right below it, found by a COLLIDER ray (fluids ignored) from just
 * above the feet down {@code depth} blocks; with no floor in reach the anchor stays at the feet. Each role keeps its own
 * depth constant and everything else about its cycle.
 * 假死/倒下流程（魔人、卡戎、希罗的受害者、薛定谔的猫）共用的地面锚点：站在地面时为玩家脚下，否则从脚下略上方向下发出
 * {@code depth} 格的 COLLIDER 射线（忽略流体）取正下方的地面；射程内没有地面时锚点保持在脚下。深度常量与流程的其余部分
 * 仍由各职业自己持有。
 */
public final class GroundAnchor {
    private GroundAnchor() {
    }

    public static Vec3d of(ServerWorld world, ServerPlayerEntity player, double depth) {
        Vec3d feet = player.getPos();
        if (player.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, depth, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }
}
