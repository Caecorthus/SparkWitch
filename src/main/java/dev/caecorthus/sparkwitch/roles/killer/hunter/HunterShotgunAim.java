package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

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

    /** A confirmed claimed player and its squared ray entry. / 已确认的声明玩家及其射线入射平方距离。 */
    public record Confirmed<T>(T target, double entrySquared) {
    }

    /**
     * One pellet ray's confirmed players in claim order, and whether an unconfirmed eligible player stopped the ray.
     * 一条弹丸射线上按声明顺序排列的已确认玩家，以及是否有未能确认的合格玩家截停了该射线。
     */
    public record Confirmation<T>(List<Confirmed<T>> hits, boolean stopped) {
    }

    /**
     * Confirms one pellet ray's claim, nearest first. The ray pierces players, so every eligible claimed player whose
     * {@code confirmedEntry} (squared, negative = the rewound volumes miss the ray) is confirmed joins the hits; the
     * first eligible one that fails stops the ray, as the first box on the revolver's ray does: the ray never passes
     * through someone the shooter saw to hit a player behind it. Ineligible players stay transparent, as in the server
     * pick.
     * 按由近到远确认一条弹丸射线的声明。射线穿透玩家，因此每个 {@code confirmedEntry}（平方距离，负数表示回溯体积与射线
     * 不相交）得到确认的合格声明玩家都加入命中；第一个确认失败的合格玩家截停该射线，与左轮射线上的第一个箱体相同：射线绝不
     * 穿过射手看到的人去打中其身后的玩家。无资格的玩家与服务端选取中一样保持“透明”。
     */
    public static <T> Confirmation<T> confirmAll(List<T> claimed, Predicate<T> eligible,
                                                 ToDoubleFunction<T> confirmedEntry) {
        List<Confirmed<T>> hits = new ArrayList<>();
        for (T candidate : claimed) {
            if (!eligible.test(candidate)) {
                continue;
            }
            double entry = confirmedEntry.applyAsDouble(candidate);
            if (entry < 0.0D) {
                return new Confirmation<>(List.copyOf(hits), true);
            }
            hits.add(new Confirmed<>(candidate, entry));
        }
        return new Confirmation<>(List.copyOf(hits), false);
    }

    /**
     * The claimed ids for ray {@code index} of a shot of {@code rayCount} rays. No fresh claim: null (the server's own
     * pick). An empty claim, which is what a malformed payload decodes to: an empty list, so every ray misses. A claim
     * shaped for another shot (the client still saw the other shell on top, e.g. a held-trigger repeat before the pop
     * synced): null, the server's own pick, as if no claim had been sent.
     * 一枪共 {@code rayCount} 条射线时第 {@code index} 条射线的声明 id。没有新鲜声明：null（服务端自行选取）。空声明
     * （格式错误的载荷即解码为此）：空列表，因此每条射线都未命中。按另一种射击形状给出的声明（客户端仍看到另一种弹药在栈顶，
     * 例如按住扳机连发时弹出尚未同步）：null，服务端自行选取，如同未发送声明。
     */
    public static @Nullable List<Integer> claimForRay(@Nullable List<List<Integer>> claim, int rayCount, int index) {
        if (claim == null) {
            return null;
        }
        if (claim.isEmpty()) {
            return List.of();
        }
        return claim.size() == rayCount && index >= 0 && index < rayCount ? claim.get(index) : null;
    }

    /** Whether a claim stored at {@code claimTick} may still be read at {@code nowTick}. / 声明是否仍可读取。 */
    public static boolean isFresh(int claimTick, int nowTick) {
        return claimTick <= nowTick && nowTick - claimTick <= MAX_CLAIM_AGE_TICKS;
    }
}
