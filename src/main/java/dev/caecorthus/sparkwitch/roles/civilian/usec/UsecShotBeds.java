package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stable rule (2026-10-09): Wathe revolver parity for sleepers, match shots only. A sleeping player's server box is a
 * 0.2 cube in the bed's head cell while the body renders along the whole bed, so Wathe's revolver kills the player
 * sleeping in a bed its ray hits ({@code RevolverItem.resolveTargetFromHitResult}). For the AXMC only the first block
 * the round met may stand for a sleeper: the stop block when nothing was pierced (FMJ always), or the first pierced
 * block, at its entry distance. A bed reached only after piercing another block never counts (the revolver cannot
 * shoot through walls), so AP still hits a sleeper's real box behind a wall but never its bed. Pure: the bed test, the
 * Wathe sleeper lookup and the AXMC eligibility come from the caller ({@link UsecShotTargets} on the server).
 * 稳定规则（2026-10-09）：仅对局射击，与 Wathe 左轮一致地处理睡觉的玩家。睡觉玩家的服务端碰撞箱是床头格中 0.2 的小立方体，
 * 身体却沿整张床渲染，因此 Wathe 左轮的射线命中床时会击杀睡在该床上的玩家（{@code RevolverItem.resolveTargetFromHitResult}）。
 * 对 AXMC 而言，只有子弹遇到的第一个方块可以代表睡觉的玩家：未穿透任何方块时为停止方块（FMJ 恒为此情况），否则为第一个被穿透的方块，
 * 取其入射处距离。先穿透其他方块后才到达的床永远不算（左轮无法穿墙射击），因此 AP 仍可命中墙后睡觉玩家的真实碰撞箱，但不会
 * 经由其床命中。纯逻辑：床的判定、Wathe 的睡觉玩家查找与 AXMC 目标资格均由调用方提供（服务端为 {@link UsecShotTargets}）。
 */
final class UsecShotBeds {
    private UsecShotBeds() {
    }

    /**
     * The bed cell a round met first, the point where it met it and that point's path distance.
     * 子弹最先遇到的床方块格、接触点以及该点的路径距离。
     */
    record BedContact(BlockPos pos, Vec3d point, double distance) {
        BedContact {
            pos = pos.toImmutable();
        }
    }

    /** A picked target and its path distance. / 选中的目标及其路径距离。 */
    record Hit<P>(P target, double distance) {
    }

    /**
     * Which bed (if any) qualifies, at what distance: the first block the round met, when {@code isBed} says it is a
     * bed. That is the first penetration when there is one, else the block the flight stopped at; null when the round
     * met no block or the first one is not a bed.
     * 哪张床（如有）符合条件、在什么距离：子弹遇到的第一个方块，且 {@code isBed} 判定其为床。有穿透时即第一次穿透，否则为飞行
     * 停止的方块；子弹未遇到方块或第一个方块不是床时为 null。
     */
    static @Nullable BedContact qualifyingBed(UsecTracer.Trace trace, Predicate<BlockPos> isBed) {
        if (trace == null || isBed == null) {
            return null;
        }
        List<UsecTracer.Penetration> penetrations = trace.penetrations();
        if (!penetrations.isEmpty()) {
            UsecTracer.Penetration first = penetrations.getFirst();
            return isBed.test(first.pos()) ? new BedContact(first.pos(), first.entry(), first.distance()) : null;
        }
        UsecTracer.BlockStop stop = trace.blockStop();
        return stop != null && isBed.test(stop.pos()) ? new BedContact(stop.pos(), stop.point(), stop.distance())
                : null;
    }

    /**
     * The sleeper standing for the qualifying bed: {@code sleeperOn} resolves it exactly as Wathe does (null for an
     * empty bed), then {@code eligible} applies the AXMC's own target rules; null when either fails.
     * 代表符合条件之床的睡觉玩家：{@code sleeperOn} 按 Wathe 的方式解析（空床为 null），再由 {@code eligible} 套用 AXMC 自身的
     * 目标规则；任一步失败时为 null。
     */
    static <P> @Nullable Hit<P> sleeperHit(UsecTracer.Trace trace, Predicate<BlockPos> isBed,
                                           Function<BedContact, P> sleeperOn, Predicate<P> eligible) {
        BedContact bed = qualifyingBed(trace, isBed);
        if (bed == null || sleeperOn == null || eligible == null) {
            return null;
        }
        P sleeper = sleeperOn.apply(bed);
        return sleeper != null && eligible.test(sleeper) ? new Hit<>(sleeper, bed.distance()) : null;
    }

    /**
     * Nearest wins on path distance between the box-picked player and the bed-picked sleeper; the box pick keeps a tie.
     * 箱体选中的玩家与经由床选中的睡觉玩家按路径距离比较，最近者胜；距离相同时保留箱体选取。
     */
    static <P> @Nullable Hit<P> nearer(@Nullable Hit<P> boxHit, @Nullable Hit<P> sleeperHit) {
        if (sleeperHit == null) {
            return boxHit;
        }
        return boxHit == null || sleeperHit.distance() < boxHit.distance() ? sleeperHit : boxHit;
    }
}
