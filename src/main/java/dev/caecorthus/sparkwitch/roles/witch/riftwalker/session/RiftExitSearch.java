package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure exit-candidate order (plan §6.5): one block in front of the gate first (the gate's front is {@code pos + facing},
 * G0 §6), then a ring of offsets around it in the gate's own frame (front laterals, beside, behind, further out, the
 * gate cell itself), then the occupant's pre-entry position. Feet positions; the server's {@link RiftExitSafety}
 * validates every candidate and the first safe one wins.
 * 纯出门候选顺序（plan §6.5）：先是门正前方一格（门的正面为 {@code pos + facing}，G0 §6），再按门自身坐标系的一圈偏移
 * （正前方两侧、门旁、门后、更远的正前方、门所在格），最后是进门前的位置。均为脚底位置；服务端的 {@link RiftExitSafety}
 * 校验每个候选，第一个安全的胜出。
 */
public final class RiftExitSearch {
    /**
     * {forward, right} offsets in blocks, in search order. Lateral steps of one block clear the 1-wide gate slab for a
     * 0.6-wide body; the gate itself has no collision, so its own cell is a valid last ring entry.
     * 按搜索顺序排列的 {前, 右} 偏移（格）。横向一格足以让 0.6 宽的身体避开 1 宽的门板；门本身无碰撞，因此门所在格是圈内
     * 最后一个有效候选。
     */
    static final double[][] RING = {
            {1.0, 0.0},
            {1.0, -0.5}, {1.0, 0.5},
            {1.0, -1.0}, {1.0, 1.0},
            {0.0, -1.0}, {0.0, 1.0},
            {-1.0, 0.0}, {-1.0, -1.0}, {-1.0, 1.0},
            {2.0, 0.0}, {2.0, -1.0}, {2.0, 1.0},
            {0.0, 0.0}
    };

    private RiftExitSearch() {
    }

    /**
     * Candidates for leaving the gate at {@code gatePos} (bottom centre) facing {@code facing}; {@code entryOrigin}
     * (nullable) is appended last. A non-horizontal facing is treated as NORTH, like the gate entity.
     * 在 {@code gatePos}（底部中心）、朝向 {@code facing} 的门处出门的候选；{@code entryOrigin}（可为空）排在最后。
     * 非水平朝向按 NORTH 处理，与门实体一致。
     */
    public static List<Vec3d> candidates(Vec3d gatePos, Direction facing, @Nullable Vec3d entryOrigin) {
        Direction front = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        Direction right = front.rotateYClockwise();
        List<Vec3d> result = new ArrayList<>(RING.length + 1);
        for (double[] offset : RING) {
            double forward = offset[0];
            double lateral = offset[1];
            result.add(new Vec3d(
                    gatePos.x + front.getOffsetX() * forward + right.getOffsetX() * lateral,
                    gatePos.y,
                    gatePos.z + front.getOffsetZ() * forward + right.getOffsetZ() * lateral));
        }
        if (entryOrigin != null) {
            result.add(entryOrigin);
        }
        return List.copyOf(result);
    }

    /** First candidate {@code safe} accepts, or null. / 第一个通过 {@code safe} 的候选，或 null。 */
    @Nullable
    public static Vec3d select(List<Vec3d> candidates, Predicate<Vec3d> safe) {
        for (Vec3d candidate : candidates) {
            if (safe.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
