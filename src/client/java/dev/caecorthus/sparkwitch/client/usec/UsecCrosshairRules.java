package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecBallistics;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecFireRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: the one decision behind the AXMC target crosshair (owner 2026-10-09, revolver parity), pure and
 * world-free. {@link UsecCrosshairTargeting} only gathers the inputs. Each piece mirrors the server's match shot, so a
 * change to the server pick belongs here too:
 * <ul>
 *     <li>aim: {@link UsecFireRules#aimDirection} on the yaw and pitch the fire packet carries;</li>
 *     <li>reach: the chambered round's open-air range ({@link UsecBallistics#fmjRange} /
 *     {@link UsecBallistics#apMaxDistance}, the shooter's Marksman), cut at the first COLLIDER, fluid-less block
 *     ({@code UsecTracerWorldProbe}); no AP penetration preview, and no drop (AP sinks nothing within 75 blocks);</li>
 *     <li>volumes: current boxes grown by {@link UsecRules#HIT_MARGIN} (the server grows rewound ones,
 *     {@code UsecShotTargets}), entered as {@link HitscanLagRules#entryDistanceSquared} counts it.</li>
 * </ul>
 * Candidates pass public state only, so the hint never tells a hidden role, faction or trait veto apart.
 * 稳定契约：AXMC 目标准星（所有者 2026-10-09，与左轮一致）背后的唯一判定，纯逻辑、不访问世界。
 * {@link UsecCrosshairTargeting} 只负责收集输入。每一项都对应服务端的对局射击，因此服务端选取若有改动，这里也要同步：
 * 瞄准方向为 {@link UsecFireRules#aimDirection}（开火数据包所带的偏航与俯仰）；射程为上膛弹种的无障碍射程（按射手精确
 * 枪手倍率），在第一个 COLLIDER、忽略流体的方块处截断（{@code UsecTracerWorldProbe}），不预示 AP 穿墙，也不计下坠（AP 在
 * 75 格内不下沉）；体积为按 {@link UsecRules#HIT_MARGIN} 扩大的当前箱体（服务端扩大的是回溯箱体，{@code UsecShotTargets}），
 * 进入判定同 {@link HitscanLagRules#entryDistanceSquared}。候选者只按公开状态筛选，因此提示无法区分隐藏的职业、阵营或词条否决。
 */
public final class UsecCrosshairRules {
    /** Growth of every candidate box: the server pick's margin. / 每个候选箱体的扩大量：即服务端选取的余量。 */
    public static final double CANDIDATE_MARGIN = UsecRules.HIT_MARGIN;

    private UsecCrosshairRules() {
    }

    /**
     * Whether the rifle could fire a round right now from the hip: held in the main hand, off cooldown (round-start lock
     * or bolt), a round chambered, and not scoped (the scope draws its own reticle instead of Wathe's crosshair).
     * 步枪此刻能否从腰射位置射出一发：主手持有、不在冷却（开局锁定或拉栓）、弹膛有弹且未开镜（开镜时由瞄准镜绘制自己的
     * 分划，而不是 Wathe 准星）。
     */
    public static boolean armed(boolean holdsRifleInMainHand, boolean coolingDown, @Nullable UsecAmmoType chamber,
                                boolean scoped) {
        return holdsRifleInMainHand && !coolingDown && chamber != null && !scoped;
    }

    /**
     * Open-air reach of the chambered round: FMJ 50 (65 at Marksman x1.3), AP 200 (260). / 上膛弹种的无障碍射程：
     * FMJ 50 格（精确枪手 x1.3 时 65），AP 200 格（260）。
     */
    public static double reach(UsecAmmoType chamber, double marksmanMultiplier) {
        return switch (chamber) {
            case FMJ -> UsecBallistics.fmjRange(marksmanMultiplier);
            case AP -> UsecBallistics.apMaxDistance(marksmanMultiplier);
        };
    }

    /** The server's shot direction for this yaw and pitch. / 该偏航与俯仰对应的服务端射击方向。 */
    public static Vec3d aim(float yaw, float pitch) {
        return UsecFireRules.aimDirection(true, yaw, pitch, yaw, pitch);
    }

    /** End of the ray before any block cuts it. / 尚未被方块截断时的射线终点。 */
    public static Vec3d rayEnd(Vec3d eye, Vec3d aim, double reach) {
        return eye.add(aim.multiply(reach));
    }

    /**
     * A player that may light the hint: Wathe's own gun-crosshair filter (alive, not spectator or creative, not
     * invisible), never the shooter, and never an active Wraith. The synced Wraith state is public (every client renders
     * by it), the server never hits an active Wraith ({@link UsecFireRules#targetEligible}), and CONTEXT's Wraith rule
     * skips it as a crosshair target, so the hint never locates a Wraith hidden from this viewer.
     * 可点亮提示的玩家：Wathe 自身枪械准星的过滤（存活、非旁观或创造、未隐身），绝不是射手本人，也绝不是激活的冤魂。
     * 已同步的冤魂状态是公开的（每个客户端都据此渲染），服务端从不命中激活冤魂（{@link UsecFireRules#targetEligible}），
     * CONTEXT 的冤魂规则也把它跳过为准星目标，因此提示绝不会暴露对本观察者隐藏的冤魂位置。
     */
    public static boolean playerCandidate(boolean self, boolean alive, boolean aliveAndSurvival, boolean invisible,
                                          boolean activeWraith) {
        return !self && alive && aliveAndSurvival && !invisible && !activeWraith;
    }

    /**
     * A live, visible Magician puppet lights the hint as its copied player would, the revolver crosshair's rule
     * ({@code MagicianPuppetAim}); the server ends a puppet on the path ({@code MagicianPuppetHits.onUsecRifleFired}).
     * Leaving puppets dark would tell a puppet from a real player.
     * 存活且可见的魔术师皮套与其复制的玩家一样点亮提示，即左轮准星的规则（{@code MagicianPuppetAim}）；服务端会结束路径上的
     * 皮套（{@code MagicianPuppetHits.onUsecRifleFired}）。若皮套不亮，就能把皮套与真玩家区分开。
     */
    public static boolean puppetCandidate(boolean alive, boolean removed, boolean invisible) {
        return alive && !removed && !invisible;
    }

    /** A candidate's current box grown by {@link #CANDIDATE_MARGIN}. / 候选者当前箱体加 {@link #CANDIDATE_MARGIN}。 */
    public static Box hitVolume(Box current) {
        return current.expand(CANDIDATE_MARGIN);
    }

    /**
     * Whether the ray {@code eye..end} (already cut at the first block) enters any volume; a volume holding the eye
     * counts, as on the server. / 射线 {@code eye..end}（已在第一个方块处截断）是否进入任一体积；包含眼睛的体积也算，
     * 与服务端一致。
     */
    public static boolean entersAny(Vec3d eye, Vec3d end, List<Box> volumes) {
        return HitscanLagRules.entryDistanceSquared(eye, end, volumes) >= 0.0;
    }
}
