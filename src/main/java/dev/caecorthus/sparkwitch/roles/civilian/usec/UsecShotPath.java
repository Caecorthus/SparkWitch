package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable contract: one rifle shot's flight path as an immutable polyline, from the shooter's eye to where the bullet
 * ended against blocks or ran out of energy. "Path distance" everywhere in the USEC fire path is the length measured
 * along this polyline from its first point, so players, Seeker devices and Magician puppets are compared on one
 * measure (nearest wins). Pure geometry: no world access.
 * 稳定契约：一次步枪射击的飞行路径，不可变折线，从射手眼睛到子弹撞上方块或能量耗尽之处。USEC 开火路径中的“路径距离”
 * 一律指沿此折线自起点量取的长度，玩家、搜寻者设备与魔术师皮套因此按同一量法比较（最近者命中）。纯几何，不访问世界。
 */
public final class UsecShotPath {
    private final List<Vec3d> points;
    private final double[] cumulative;

    /** At least one point (a zero-length path); never null. / 至少一个点（零长度路径）；不得为 null。 */
    public UsecShotPath(List<Vec3d> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("A USEC shot path needs at least one point");
        }
        this.points = List.copyOf(points);
        this.cumulative = new double[this.points.size()];
        for (int i = 1; i < this.points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + this.points.get(i - 1).distanceTo(this.points.get(i));
        }
    }

    /** Polyline vertices in flight order (the muzzle first). / 按飞行顺序排列的折线顶点（枪口在前）。 */
    public List<Vec3d> points() {
        return points;
    }

    public Vec3d start() {
        return points.getFirst();
    }

    public Vec3d end() {
        return points.getLast();
    }

    /** Total path length. / 路径总长。 */
    public double length() {
        return cumulative[cumulative.length - 1];
    }

    /** Path distance of vertex {@code index}. / 第 {@code index} 个顶点的路径距离。 */
    public double distanceAt(int index) {
        return cumulative[index];
    }

    /**
     * The point at a path distance, clamped to the path. / 指定路径距离处的点，超出范围时钳制到路径端点。
     */
    public Vec3d pointAt(double distance) {
        if (!(distance > 0.0)) {
            return start();
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            double segment = cumulative[i + 1] - cumulative[i];
            if (distance <= cumulative[i + 1] && segment > 0.0) {
                return lerp(points.get(i), points.get(i + 1), (distance - cumulative[i]) / segment);
            }
        }
        return end();
    }

    /**
     * Path distance at which the path first enters any of {@code volumes} (0 when its start is inside one), searching
     * only up to {@code limit}; -1 when it enters none in time. Segments are tested in flight order, so the first hit is
     * the nearest along the path.
     * 路径首次进入任一 {@code volumes} 处的路径距离（起点在体积内时为 0），只搜索到 {@code limit} 为止；在此之前未进入任何体积时
     * 返回 -1。按飞行顺序逐段检测，因此第一个命中就是沿路径最近的命中。
     */
    public double entryDistance(List<Box> volumes, double limit) {
        if (volumes == null || volumes.isEmpty() || !(limit >= 0.0)) {
            return -1.0;
        }
        if (points.size() == 1) {
            return HitscanLagRules.entryDistanceSquared(start(), start(), volumes) == 0.0 ? 0.0 : -1.0;
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            double from = cumulative[i];
            if (from > limit) {
                break;
            }
            double segment = cumulative[i + 1] - from;
            if (!(segment > 0.0)) {
                continue;
            }
            double cut = Math.min(segment, limit - from);
            Vec3d a = points.get(i);
            Vec3d b = cut >= segment ? points.get(i + 1) : lerp(a, points.get(i + 1), cut / segment);
            double squared = HitscanLagRules.entryDistanceSquared(a, b, volumes);
            if (squared >= 0.0) {
                return from + Math.sqrt(squared);
            }
        }
        return -1.0;
    }

    /**
     * The vertices of the path cut at a path distance (the cut point becomes the last vertex). / 在指定路径距离处截断后的顶点
     * （截断点成为最后一个顶点）。
     */
    public List<Vec3d> pointsUpTo(double distance) {
        if (!(distance < length())) {
            return points;
        }
        List<Vec3d> cut = new ArrayList<>();
        cut.add(start());
        for (int i = 1; i < points.size() && cumulative[i] < distance; i++) {
            cut.add(points.get(i));
        }
        cut.add(pointAt(distance));
        return List.copyOf(cut);
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double fraction) {
        double t = Math.max(0.0, Math.min(1.0, fraction));
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
