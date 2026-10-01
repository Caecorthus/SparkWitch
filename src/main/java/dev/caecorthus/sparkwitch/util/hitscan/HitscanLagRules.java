package dev.caecorthus.sparkwitch.util.hitscan;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure lag-compensation geometry for server hitscan weapons. A shooter aims at other players as its client draws
 * them, which trails their server position by the round trip plus the client's interpolation; a fast target
 * therefore sits outside its current server box when the shot arrives. The server instead tests the target's
 * recent boxes inside the shooter's rewind window, swept between consecutive ticks. Volumes grow only by the
 * weapon's own small margin: every volume is a place the target really stood, so a block-clipped ray cannot reach
 * it through a floor or wall.
 * 服务端即时射线武器的纯延迟补偿几何。射手按客户端绘制的位置瞄准其他玩家，而该位置落后于服务端位置一个往返延迟
 * 加客户端插值；目标移动越快，开枪数据包到达时越会偏出其当前服务端箱体。因此服务端改为检测射手回溯窗口内
 * 目标最近的箱体，并在相邻刻之间做扫掠。体积只按武器自身的小余量扩大：每个体积都是目标真实站过的位置，
 * 因此被方块截断的射线无法穿过地板或墙壁命中。
 */
public final class HitscanLagRules {
    /**
     * Ping-independent view delay: the 3-step interpolation of other players, their 2-tick tracker interval and one
     * tick of sampling jitter. / 与延迟无关的视图滞后：其他玩家的 3 步插值、2 刻追踪间隔与 1 刻采样抖动。
     */
    public static final int CLIENT_VIEW_DELAY_TICKS = 6;
    /** Rewind cap: one second of server ticks. / 回溯上限：一秒的服务端刻。 */
    public static final int MAX_REWIND_TICKS = 20;
    /**
     * Samples kept per player. Shots can run after the end-of-tick sample in the same tick, so the window
     * {@code now - MAX_REWIND_TICKS .. now} spans one more sample than the cap.
     * 每名玩家保留的样本数。开枪可能在同一刻的刻末采样之后处理，因此窗口 {@code now - MAX_REWIND_TICKS .. now}
     * 比回溯上限多一个样本。
     */
    public static final int HISTORY_TICKS = MAX_REWIND_TICKS + 1;
    /**
     * Consecutive samples farther apart than this (teleport, respawn) are never swept into one volume.
     * 相邻样本相距超过该值（传送、重生）时不会合并扫掠。
     */
    public static final double MAX_SWEEP_STEP = 1.5;

    private static final int MAX_LATENCY_MS = 60_000;

    private HitscanLagRules() {
    }

    /** Ticks of target history a shot may rewind for a shooter with this ping. / 该延迟的射手可回溯的刻数。 */
    public static int rewindTicks(int latencyMs) {
        int pingTicks = (int) Math.ceil(MathHelper.clamp(latencyMs, 0, MAX_LATENCY_MS) / 50.0);
        return MathHelper.clamp(CLIENT_VIEW_DELAY_TICKS + pingTicks, CLIENT_VIEW_DELAY_TICKS, MAX_REWIND_TICKS);
    }

    /**
     * Hit volumes from {@code newestFirst} boxes: each consecutive pair within {@link #MAX_SWEEP_STEP} is swept into
     * their union, a lone or jumped sample stays on its own; all grow by {@code expansion}.
     * 由最新在前的箱体生成命中体积：相距不超过 {@link #MAX_SWEEP_STEP} 的相邻两箱合并为并集，孤立或跳变的样本单独保留；
     * 全部按 {@code expansion} 扩大。
     */
    public static List<Box> sweptVolumes(List<Box> newestFirst, double expansion) {
        double grow = Double.isFinite(expansion) ? Math.max(0.0, expansion) : 0.0;
        List<Box> volumes = new ArrayList<>(newestFirst.size());
        for (int index = 0; index < newestFirst.size(); index++) {
            Box box = newestFirst.get(index);
            Box next = index + 1 < newestFirst.size() ? newestFirst.get(index + 1) : null;
            if (next != null && box.getCenter().distanceTo(next.getCenter()) <= MAX_SWEEP_STEP) {
                volumes.add(box.union(next).expand(grow));
            } else if (index == 0 || !sweptWithPrevious(newestFirst, index)) {
                volumes.add(box.expand(grow));
            }
        }
        return volumes;
    }

    /**
     * Squared distance from {@code start} to the earliest entry into any volume along {@code start → end}; 0 when
     * {@code start} is inside one, -1 on a miss. / 沿线段最早进入任一体积处的平方距离；起点在内为 0，未命中为 -1。
     */
    public static double entryDistanceSquared(Vec3d start, Vec3d end, List<Box> volumes) {
        double nearest = -1.0;
        for (Box volume : volumes) {
            double distance;
            if (volume.contains(start)) {
                distance = 0.0;
            } else {
                Optional<Vec3d> entry = volume.raycast(start, end);
                if (entry.isEmpty()) {
                    continue;
                }
                distance = start.squaredDistanceTo(entry.get());
            }
            if (nearest < 0.0 || distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    private static boolean sweptWithPrevious(List<Box> newestFirst, int index) {
        return newestFirst.get(index - 1).getCenter().distanceTo(newestFirst.get(index).getCenter()) <= MAX_SWEEP_STEP;
    }
}
