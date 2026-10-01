package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Pure search over ordinary door geometry; the caller validates every destination. / 普通门几何的纯搜索；调用者校验每个落点。 */
final class FisherExitSearch {
    private static final double MARGIN = 0.01;

    private FisherExitSearch() {
    }

    static List<Vec3d> candidates(Vec3d feet, Box body, List<Box> doors) {
        List<Vec3d> direct = new ArrayList<>();
        List<Vec3d> lateral = new ArrayList<>();
        for (Box door : doors) {
            boolean acrossX = door.getLengthX() < door.getLengthZ();
            Vec3d negative = acrossX
                    ? feet.add(door.minX - body.maxX - MARGIN, 0, 0)
                    : feet.add(0, 0, door.minZ - body.maxZ - MARGIN);
            Vec3d positive = acrossX
                    ? feet.add(door.maxX - body.minX + MARGIN, 0, 0)
                    : feet.add(0, 0, door.maxZ - body.minZ + MARGIN);
            List<Vec3d> sides = new ArrayList<>(List.of(negative, positive));
            sides.sort(Comparator.comparingDouble(feet::squaredDistanceTo));
            direct.addAll(sides);
            for (double offset : new double[]{0.5, -0.5, 1.0, -1.0, 1.5, -1.5}) {
                for (Vec3d side : sides) {
                    lateral.add(acrossX ? side.add(0, 0, offset) : side.add(offset, 0, 0));
                }
            }
        }
        direct.sort(Comparator.comparingDouble(feet::squaredDistanceTo));
        direct.addAll(lateral);
        return List.copyOf(direct);
    }

    static @Nullable Vec3d select(List<Vec3d> candidates, @Nullable Vec3d lastSafe, Predicate<Vec3d> safe) {
        for (Vec3d candidate : candidates) {
            if (safe.test(candidate)) {
                return candidate;
            }
        }
        return lastSafe != null && safe.test(lastSafe) ? lastSafe : null;
    }

    static boolean insideBounds(Box body, int bottom, int top, @Nullable Box playArea) {
        return Double.isFinite(body.minX) && Double.isFinite(body.maxX)
                && Double.isFinite(body.minY) && Double.isFinite(body.maxY)
                && Double.isFinite(body.minZ) && Double.isFinite(body.maxZ)
                && body.minY >= bottom && body.maxY <= top
                && (playArea == null || body.minX >= playArea.minX && body.maxX <= playArea.maxX
                && body.minY >= playArea.minY && body.maxY <= playArea.maxY
                && body.minZ >= playArea.minZ && body.maxZ <= playArea.maxZ);
    }
}
