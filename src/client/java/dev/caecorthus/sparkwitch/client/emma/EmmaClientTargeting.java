package dev.caecorthus.sparkwitch.client.emma;

import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/** Mirrors GrandWitchTargeting's public ray geometry, not its server role validation. / 仅同步服务端公共射线几何，不读取隐藏职业资格。 */
public final class EmmaClientTargeting {
    private EmmaClientTargeting() {
    }

    public static @Nullable PlayerEntity findTarget(ClientPlayerEntity viewer) {
        Vec3d start = viewer.getEyePos();
        Vec3d end = start.add(viewer.getRotationVec(1.0F).multiply(EmmaRules.TARGET_RANGE));
        var block = viewer.getWorld().raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, viewer));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        double closest = start.squaredDistanceTo(end);
        PlayerEntity selected = null;
        for (PlayerEntity candidate : viewer.getWorld().getPlayers()) {
            // Invisible and secretly forbidden roles remain candidates; rejection belongs to the server.
            // 隐身及秘密禁用职业仍是候选者，是否拒绝由服务端裁决。
            if (candidate == viewer || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
                continue;
            }
            Box box = candidate.getBoundingBox();
            Vec3d hit = box.contains(start) ? start : box.raycast(start, end).orElse(null);
            if (hit == null) {
                continue;
            }
            double distance = start.squaredDistanceTo(hit);
            if (distance < closest || (selected == null && distance <= closest)) {
                closest = distance;
                selected = candidate;
            }
        }
        return selected;
    }
}
