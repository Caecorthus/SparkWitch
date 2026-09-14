package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/** Server-owned targeting shared by both Grand Witch skills. / 两个大魔女技能共用的服务端选人判定。 */
public final class GrandWitchTargeting {
    public static final double RANGE = 8.0D;

    private GrandWitchTargeting() {
    }

    public static @Nullable ServerPlayerEntity findTarget(ServerPlayerEntity caster, @Nullable UUID requested) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(RANGE));
        BlockHitResult block = caster.getServerWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        double closest = start.squaredDistanceTo(end);
        ServerPlayerEntity selected = null;
        for (ServerPlayerEntity candidate : caster.getServerWorld().getPlayers()) {
            if (candidate == caster || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
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
        return selected != null && (requested == null || requested.equals(selected.getUuid())) ? selected : null;
    }
}
