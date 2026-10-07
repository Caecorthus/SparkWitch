package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure exit-candidate order (plan §6.5): one block in front of the gate first (the gate's front is {@code pos + facing},
 * G0 §6), then a ring of offsets around it in the gate's own frame (front laterals, beside, behind, the gate cell
 * itself, further out), then the occupant's pre-entry position. Feet positions; the server's {@link RiftExitSafety}
 * validates every candidate and the first safe one wins. Every ring cell must also be reachable from the gate opening
 * (B-2): the standing body swept from the gate to the cell ({@link #sweptBody}) may not touch a block, so no exit lands
 * behind a wall, cuts a wall corner, or skips into a locked cabin. The pre-entry position is exempt: the occupant
 * stood there before entering.
 * 纯出门候选顺序（plan §6.5）：先是门正前方一格（门的正面为 {@code pos + facing}，G0 §6），再按门自身坐标系的一圈偏移
 * （正前方两侧、门旁、门后、门所在格、更远的正前方），最后是进门前的位置。均为脚底位置；服务端的 {@link RiftExitSafety}
 * 校验每个候选，第一个安全的胜出。圈内每一格还必须能从门口到达（B-2）：站立碰撞箱从门扫到该格（{@link #sweptBody}）时
 * 不得碰到方块，因此出口绝不会落在墙后、斜穿墙角或跳进上锁的车厢。进门前位置不受此限：门内玩家进门前就站在那里。
 */
public final class RiftExitSearch {
    /**
     * Boxes are shrunk by this much before the sweep so faces that merely touch never count.
     * 扫掠前碰撞箱收缩的余量，仅相互接触的面不算碰撞。
     */
    static final double SWEEP_EPSILON = 1.0E-4;

    /**
     * {forward, right} offsets in blocks, in search order. Lateral steps of one block clear the 1-wide gate slab for a
     * 0.6-wide body. The gate has no collision, so its own cell is valid; it comes after the near ring (standing in the
     * slab keeps the gate in a user's crosshair) and before the far row, which is reached only through the near one.
     * 按搜索顺序排列的 {前, 右} 偏移（格）。横向一格足以让 0.6 宽的身体避开 1 宽的门板。门本身无碰撞，因此门所在格有效；
     * 它排在近圈之后（站在门板里会让门一直留在使用者的准星上）、远排之前（远排只能经过近圈到达）。
     */
    static final double[][] RING = {
            {1.0, 0.0},
            {1.0, -0.5}, {1.0, 0.5},
            {1.0, -1.0}, {1.0, 1.0},
            {0.0, -1.0}, {0.0, 1.0},
            {-1.0, 0.0}, {-1.0, -1.0}, {-1.0, 1.0},
            {0.0, 0.0},
            {2.0, 0.0}, {2.0, -1.0}, {2.0, 1.0}
    };

    private RiftExitSearch() {
    }

    /**
     * Candidates for leaving the gate at {@code gatePos} (bottom centre) facing {@code facing}; {@code entryOrigin}
     * (nullable) is appended last, as the same instance. A non-horizontal facing is treated as NORTH, like the gate
     * entity.
     * 在 {@code gatePos}（底部中心）、朝向 {@code facing} 的门处出门的候选；{@code entryOrigin}（可为空）以同一实例排在最后。
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

    /**
     * Where the reachability sweep for {@code candidate} starts: the gate position for every ring cell, null (no
     * sweep) for the pre-entry position, recognised by identity because {@link #candidates} appends that instance.
     * 候选 {@code candidate} 的可达性扫掠起点：圈内每格为门的位置；进门前位置为 null（不扫掠），按实例识别，因为
     * {@link #candidates} 追加的正是该实例。
     */
    @Nullable
    public static Vec3d sweepStart(Vec3d gatePos, Vec3d candidate, @Nullable Vec3d entryOrigin) {
        return entryOrigin != null && candidate == entryOrigin ? null : gatePos;
    }

    /**
     * The volume a standing body passes through from the gate to {@code feet}: {@code bodyAtGate} (the body box at
     * {@code gatePos}) stretched by the move and shrunk by {@link #SWEEP_EPSILON}. Conservative for diagonal cells (the
     * whole rectangle must be clear), so a wall corner can never be cut.
     * 站立身体从门移动到 {@code feet} 时经过的空间：{@code bodyAtGate}（位于 {@code gatePos} 的身体碰撞箱）按位移拉伸，再收缩
     * {@link #SWEEP_EPSILON}。对斜向格子取保守值（整个矩形都必须空），因此永远不会斜穿墙角。
     */
    public static Box sweptBody(Box bodyAtGate, Vec3d gatePos, Vec3d feet) {
        return bodyAtGate.stretch(feet.x - gatePos.x, feet.y - gatePos.y, feet.z - gatePos.z).contract(SWEEP_EPSILON);
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
