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

/** Server-owned aim for Witch Factor and Emma; recruitment no longer aims (owner request 2026-10-06).
 * 魔女因子与艾玛共用的服务端瞄准判定；招募已不再瞄准（所有者 2026-10-06 要求）。 */
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
            // Spectators are neither targets nor shields: a Rift Gate occupant is an ALIVE spectator (Riftwalker D3)
            // that Witch Factor and Emma must never pick, nor let it block the player behind its gate.
            // 旁观者既不是目标也不会挡住射线：裂隙门内的玩家是存活旁观者（隙行者 D3），魔女因子与艾玛都不能选中它，
            // 也不能让它挡住门后的玩家。
            if (candidate == caster || candidate.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
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
