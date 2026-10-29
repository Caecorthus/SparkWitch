package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure rules of the shotgun's aim claim (revolver parity, 2026-10-21). The server's own pick tests every box a target
 * held over the shooter's rewind window, so against a running target its hit zone stretches ahead of and behind the
 * body the shooter saw, by the target's speed times the delay. The revolver hits only what the shooter's client shows.
 * The shotgun now does the same: its client names the players its view ray entered, nearest first, and the server only
 * confirms that claim against the rewound volumes.
 * 猎枪瞄准声明的纯规则（与左轮一致，2026-10-21）。服务端自行选取时会检测目标在射手回溯窗口内占据过的每个箱体，
 * 因此对奔跑中的目标，其命中区会沿移动方向在射手看到的身体前后拉长，长度为目标速度乘以延迟。左轮只命中射手客户端
 * 所显示的目标。猎枪现在与其一致：客户端按由近到远列出其视线射线进入的玩家，服务端只依据回溯体积确认该声明。
 */
public final class HunterShotgunAim {
    /**
     * Spread rays per shot: the centre plus up/down, left/right and the four diagonals, each
     * {@link HunterRules#SHOTGUN_SPREAD_DEGREES} from the centre.
     * 每次射击的散射射线数：中心、上下、左右与四角，各偏离中心 {@link HunterRules#SHOTGUN_SPREAD_DEGREES}。
     */
    public static final int RAY_COUNT = 9;
    /** Claimed players kept per ray, nearest first (the old single-ray cap). / 每条射线保留的声明玩家数（由近到远，沿用旧单射线上限）。 */
    public static final int MAX_CLAIMED_PER_RAY = 8;
    /**
     * Client claim margin, the same growth as the server pick and the target crosshair.
     * 客户端声明的箱体扩大量，与服务端选取和目标准星相同。
     */
    public static final double CLAIM_BOX_EXPANSION = 0.1D;
    /**
     * Server confirmation growth: the pick margin plus 0.4 for the client's interpolated path between server samples
     * and a smoothed ping slightly behind the real one. It only bounds how far a forged claim may reach.
     * 服务端确认时的扩大量：选取余量加 0.4，用于覆盖客户端在服务端样本之间的插值路径，以及略低于实际值的平滑延迟。
     * 它只限制伪造声明能够到达的范围。
     */
    public static final double CONFIRM_BOX_EXPANSION = 0.5D;
    /**
     * A claim is read by the use packet sent right after it; one tick of slack covers a split task drain.
     * 声明由紧随其后的使用数据包读取；允许一刻的余量以覆盖被拆分到两刻的任务处理。
     */
    public static final int MAX_CLAIM_AGE_TICKS = 1;

    private HunterShotgunAim() {
    }

    /** A player the client could see on its ray: entity id and current client box. / 客户端射线上可见的玩家。 */
    public record Candidate(int id, Box box) {
    }

    /**
     * Ids of the candidates whose box, grown by {@link #CLAIM_BOX_EXPANSION}, the segment {@code start → end} enters,
     * nearest entry first, at most {@link #MAX_CLAIMED_PER_RAY}. Eligibility is left to the server.
     * 线段进入其扩大箱体的候选 id，按进入距离由近到远，最多 {@link #MAX_CLAIMED_PER_RAY} 个。资格由服务端判定。
     */
    public static List<Integer> claimOrder(Vec3d start, Vec3d end, List<Candidate> candidates) {
        record Entry(int id, double distance) {
        }
        List<Entry> entries = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double distance = HitscanLagRules.entryDistanceSquared(start, end,
                    List.of(candidate.box().expand(CLAIM_BOX_EXPANSION)));
            if (distance >= 0.0D) {
                entries.add(new Entry(candidate.id(), distance));
            }
        }
        entries.sort(Comparator.comparingDouble(Entry::distance));
        return entries.stream().limit(MAX_CLAIMED_PER_RAY).map(Entry::id).toList();
    }

    /**
     * The first claimed player the server finds eligible decides the shot, as the first box on the revolver's ray does:
     * confirmed, it is hit; unconfirmed, the shot misses rather than passing through to someone behind it. Ineligible
     * players stay transparent, as in the server pick.
     * 服务端认定有资格的第一个声明玩家决定这一枪，与左轮射线上的第一个箱体相同：确认则命中；未能确认则未命中，
     * 而不会穿过它打中后面的人。无资格的玩家与服务端选取中一样保持“透明”。
     */
    public static <T> @Nullable T decide(List<T> claimed, Predicate<T> eligible, Predicate<T> confirmed) {
        for (T candidate : claimed) {
            if (eligible.test(candidate)) {
                return confirmed.test(candidate) ? candidate : null;
            }
        }
        return null;
    }

    /** Whether a claim stored at {@code claimTick} may still be read at {@code nowTick}. / 声明是否仍可读取。 */
    public static boolean isFresh(int claimTick, int nowTick) {
        return claimTick <= nowTick && nowTick - claimTick <= MAX_CLAIM_AGE_TICKS;
    }
}
