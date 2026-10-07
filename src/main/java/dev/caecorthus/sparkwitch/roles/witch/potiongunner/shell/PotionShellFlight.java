package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Pure, side-neutral flight helpers for the shell entity (server and client prediction). Flat flight (owner addendum
 * 1) is decided by {@link PotionGunnerRules#isFlatTick} on the path length at the start of the tick; a flat tick moves
 * by the launch velocity unchanged (no gravity, no drag), and the first non-flat tick latches vanilla thrown physics.
 * During flat flight the shell moves exactly {@link PotionGunnerRules#MUZZLE_SPEED} per tick, so the measured
 * straight-line distance from the launch point is snapped to that step grid before the rule is applied: the rule's
 * boundary (exactly 50.0 at the start of the 21st tick) then never depends on float rounding or on the client's
 * network-quantised position, and both sides switch on the same tick. Result: 20 flat ticks covering exactly 50
 * blocks.
 * 纯计算、两端通用的炮弹飞行辅助（服务端与客户端预测共用）。平直飞行（所有者补充 1）由 {@link PotionGunnerRules#isFlatTick}
 * 依据本刻开始时的路径长度判定；平直刻按发射速度原样移动（无重力、无阻力），第一个非平直刻起锁定为原版投掷物物理。
 * 平直飞行时炮弹每刻恰好移动 {@link PotionGunnerRules#MUZZLE_SPEED}，因此先把测得的距发射点直线距离对齐到该步长网格，
 * 再套用规则：规则边界（第 21 刻开始时恰为 50.0）不再受浮点舍入或客户端网络量化位置影响，两端在同一刻切换。
 * 结果为 20 个平直刻、恰好 50 格。
 */
public final class PotionShellFlight {
    private PotionShellFlight() {
    }

    /**
     * Path length at the start of a flat tick: {@code straightDistance} snapped to the nearest multiple of the muzzle
     * speed; non-finite or negative input is treated as out of range.
     * 平直刻开始时的路径长度：把 {@code straightDistance} 对齐到最近的初速整数倍；非有限或负值视为超出范围。
     */
    public static double flatPathTravelled(double straightDistance) {
        if (!Double.isFinite(straightDistance) || straightDistance < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double step = PotionGunnerRules.MUZZLE_SPEED;
        return Math.round(straightDistance / step) * step;
    }

    /**
     * Whether the tick that starts {@code straightDistance} blocks from the launch point is flat
     * ({@link PotionGunnerRules#isFlatTick} on the snapped path length).
     * 从距发射点 {@code straightDistance} 格处开始的这一刻是否为平直刻（对对齐后的路径长度套用规则）。
     */
    public static boolean isFlatTick(double straightDistance) {
        return PotionGunnerRules.isFlatTick(flatPathTravelled(straightDistance));
    }

    /**
     * Path length at the end of the flat tick that starts {@code straightDistance} blocks from the launch point: the
     * snapped start-of-tick length plus one muzzle-speed step. A Riftwalker Rift Gate pass (owner decision D18) re-bases
     * the launch point this far behind the shell's new position, so the flat range is counted along the whole path,
     * gate jumps included, and stays on the step grid: still {@link #flatTicks()} flat ticks in total.
     * 从距发射点 {@code straightDistance} 格处开始的平直刻结束时的路径长度：对齐后的刻初长度加一个初速步长。穿过隙行者的
     * 裂隙门（所有者决定 D18）时，发射点被重设到炮弹新位置后方这么远处，因此平直射程沿整条路径（含穿门跳转）累计，
     * 并保持在步长网格上：总共仍是 {@link #flatTicks()} 个平直刻。
     */
    public static double flatPathAfterTick(double straightDistance) {
        return flatPathTravelled(straightDistance) + PotionGunnerRules.MUZZLE_SPEED;
    }

    /** Number of flat ticks after launch. / 发射后的平直刻数。 */
    public static int flatTicks() {
        int ticks = 0;
        while (isFlatTick(ticks * (double) PotionGunnerRules.MUZZLE_SPEED)) {
            ticks++;
        }
        return ticks;
    }

    /**
     * Whether a player stops the shell (D-R4): only a survival/adventure, Wathe-living round participant who is not
     * under SparkTraits Last Escape; everyone else is someone the blast could never catch, so the shell flies on.
     * The client passes {@code lastEscape = false} (server-only state).
     * 玩家是否挡下炮弹（D-R4）：只有生存/冒险模式、Wathe 判定存活的对局参与者，且不处于 SparkTraits 最后逃脱；
     * 其余玩家都是爆炸永远波及不到的人，炮弹继续飞行。客户端传入 {@code lastEscape = false}（仅服务端状态）。
     */
    public static boolean playerStopsShell(boolean aliveAndSurvival, boolean playingAndAlive, boolean lastEscape) {
        return aliveAndSurvival && playingAndAlive && !lastEscape;
    }

    /**
     * Whether the server's lag-compensated player hit takes this tick: it must exist ({@code ≥ 0}) and enter strictly
     * nearer along the path than the vanilla collision (a block, closed door, Rift Gate or current box; positive
     * infinity on a miss). A tie leaves the tick to vanilla. Both are squared distances from the tick's start.
     * 服务端的延迟补偿玩家命中是否接管本刻：它必须存在（{@code ≥ 0}），且沿路径的进入点严格近于原版碰撞（方块、关闭的门、
     * 裂隙门或当前箱体；未命中为正无穷）。距离相等时交给原版处理。两者都是距本刻起点的平方距离。
     */
    public static boolean lagCompensatedHitFirst(double laggedEntrySquared, double vanillaEntrySquared) {
        return laggedEntrySquared >= 0.0 && laggedEntrySquared < vanillaEntrySquared;
    }

    /**
     * Squared distance from {@code from} to where the path {@code from → to} first enters a player's lag-compensated
     * {@code volumes} (each grown by {@code margin}), or -1 on a miss. A path that enters a grown volume is hit as in
     * vanilla. A path that starts inside the margin still hits when it enters the player's real (ungrown) volume,
     * which vanilla's ray misses. A start inside the real volume counts as 0, but only once {@code startInsideCounts}
     * (the shell has moved), so someone pressed against the gunner never stops the shell at the muzzle.
     * 路径 {@code from → to} 首次进入玩家延迟补偿体积 {@code volumes}（各自扩大 {@code margin}）处到 {@code from} 的平方距离，
     * 未命中为 -1。进入扩大后体积的路径与原版一样算命中。起点位于余量内的路径，只要进入玩家真实（未扩大）体积仍算命中，
     * 原版射线会漏掉这种情况。起点位于真实体积内记为 0，但仅当 {@code startInsideCounts}（炮弹已移动过）时成立，
     * 因此紧贴药炮手的人不会在炮口处挡下炮弹。
     */
    public static double laggedEntrySquared(Vec3d from, Vec3d to, List<Box> volumes, double margin,
                                            boolean startInsideCounts) {
        double nearest = -1.0;
        for (Box volume : volumes) {
            Box real = volume.contract(margin);
            double entry;
            if (real.contains(from)) {
                if (!startInsideCounts) {
                    continue;
                }
                entry = 0.0;
            } else {
                Optional<Vec3d> point = volume.contains(from) ? real.raycast(from, to) : volume.raycast(from, to);
                if (point.isEmpty()) {
                    continue;
                }
                entry = from.squaredDistanceTo(point.get());
            }
            if (nearest < 0.0 || entry < nearest) {
                nearest = entry;
            }
        }
        return nearest;
    }

    /** {@code 0xRRGGBB} as trail dust colour components. / 把 {@code 0xRRGGBB} 拆成烟迹粉尘颜色分量。 */
    public static float[] trailRgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
    }
}
