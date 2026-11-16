package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 规划大师 Master Planner — a passenger who hands out tasks (PR #219, rebuilt in review).
 * <ul>
 *   <li>Recommend (ability key): {@link #SKILL_COST} coins; adds one native Wathe task of a type the target does not
 *   hold to the player under the crosshair within {@link #REACH} blocks in line of sight, or to the planner when
 *   nothing is aimed at. Ready {@link #INITIAL_COOLDOWN_TICKS} after the role is dealt, then
 *   {@link #COOLDOWN_TICKS} after each recommendation. A refused request costs nothing and starts no cooldown.</li>
 *   <li>A recommendation lasts {@link #RECOMMENDATION_LIFETIME_TICKS}; it never drains sanity. Completing it pays the
 *   target {@link #REWARD_COINS_CIVILIAN} coins (Wathe civilian faction) or {@link #REWARD_COINS_OTHER} (everyone
 *   else) and {@link #REWARD_MOOD} sanity; on expiry the task silently disappears without pay.</li>
 *   <li>Coins: {@link #OWN_TASK_COINS} for every task the planner completes.</li>
 *   <li>Shop: the task slip ({@link #SLIP_PRICE}); whoever holds it rerolls the tasks of the player under the
 *   crosshair, or their own.</li>
 * </ul>
 * 规划大师——给别人安排任务的乘客（PR #219，审核中重建）。
 * 推荐（技能键）：花费 {@link #SKILL_COST} 金币，为准星下 {@link #REACH} 格内、视线可及的玩家（无目标时为自己）添加一项其
 * 尚未持有类型的 Wathe 原生任务。发牌后 {@link #INITIAL_COOLDOWN_TICKS} 就绪，之后每次推荐冷却 {@link #COOLDOWN_TICKS}。
 * 被拒绝的请求不扣钱、不进入冷却。
 * 推荐任务持续 {@link #RECOMMENDATION_LIFETIME_TICKS}，从不消耗理智；完成后目标获得 {@link #REWARD_COINS_CIVILIAN}（Wathe
 * 平民阵营）或 {@link #REWARD_COINS_OTHER}（其他）金币与 {@link #REWARD_MOOD} 理智；超时则静默消失，不结算。
 * 金币：规划大师每完成一个任务 +{@link #OWN_TASK_COINS}。
 * 商店：任务单（{@link #SLIP_PRICE}）；持有者可重排准星下玩家或自己的任务。
 */
public final class PlannerRules {

    public static final Identifier ROLE_ID = SparkWitch.id("planner_master");

    /** Identity colour E8C46A. / 身份色 E8C46A。 */
    public static final int COLOR = 0xE8C46A;

    /** Stable item contract. / 稳定物品契约。 */
    public static final Identifier TASK_SLIP_ID = SparkWitch.id("planner_task_slip");

    /** Forced-cooldown store id for the recommendation. / 推荐的强制冷却存储 id。 */
    public static final Identifier RECOMMEND_SKILL_ID = SparkWitch.id("planner_recommend");

    /** Recommendation: 25 coins, 45 s after the deal, 70 s after each use. / 推荐：25 金币，发牌后 45 秒，之后 70 秒。 */
    public static final int SKILL_COST = 25;
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;
    public static final int COOLDOWN_TICKS = 70 * 20;

    /** Reach of the recommendation and the task slip, in blocks. / 推荐与任务单的距离（格）。 */
    public static final double REACH = 32.0D;

    /** A recommendation lasts 60 s. / 推荐任务存在 60 秒。 */
    public static final int RECOMMENDATION_LIFETIME_TICKS = 60 * 20;

    /** Completion reward: 50 coins for Wathe civilians, 25 for everyone else, plus 0.75 sanity. / 完成奖励。 */
    public static final int REWARD_COINS_CIVILIAN = 50;
    public static final int REWARD_COINS_OTHER = 25;
    public static final float REWARD_MOOD = 0.75F;

    /** Coins for every task the planner completes. / 规划大师每完成一个任务获得的金币。 */
    public static final int OWN_TASK_COINS = 75;

    /** Shop price of the task slip. / 任务单的商店价格。 */
    public static final int SLIP_PRICE = 50;

    /**
     * Owner 2026-11-16 (Q1): the {@link #OWN_TASK_COINS} are paid for EVERY task the planner completes. {@code false}
     * would pay them only for a recommendation the planner completes on themselves.
     * 所有者 2026-11-16（Q1）：规划大师完成的每个任务都发放 {@link #OWN_TASK_COINS}。若为 {@code false}，则只在规划大师完成
     * 推荐给自己的任务时发放。
     */
    public static final boolean OWN_TASK_COINS_FOR_EVERY_TASK = true;

    /**
     * Owner 2026-11-16 (Q2): with nobody under the crosshair the recommendation goes to the planner, with the normal
     * reward. {@code false} would require aiming at another player.
     * 所有者 2026-11-16（Q2）：准星下没有玩家时推荐给规划大师自己，奖励照常。若为 {@code false}，则必须对准另一名玩家。
     */
    public static final boolean SELF_RECOMMEND_ALLOWED = true;

    /** Stable shop entry id (the item id). / 稳定的商店条目 id（即物品 id）。 */
    public static final String TASK_SLIP_ENTRY = TASK_SLIP_ID.toString();

    private static final PlayerMoodComponent.Task[] TASK_TYPES = PlayerMoodComponent.Task.values();

    private PlannerRules() {
    }

    /** Raw-role check (never a disguise). / 原始身份判断（不看伪装）。 */
    public static boolean isPlanner(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** What the server does with a recommendation request. / 服务端如何处理一次推荐请求。 */
    public enum RecommendVerdict {
        /** Recommend a task. / 推荐任务。 */
        USE,
        /** Refuse silently (role, round, control, cooldown). / 静默拒绝（职业、回合、受控、冷却）。 */
        REFUSE,
        /** Refuse with the Fear feedback. / 拒绝并给出恐惧反馈。 */
        FEARED,
        /** Nobody valid to recommend to (gone, out of reach or out of sight). / 没有可推荐的目标（已离开、超出距离或视线）。 */
        NO_TARGET,
        /** Refuse: fewer than {@link #SKILL_COST} coins. / 拒绝：金币不足 {@link #SKILL_COST}。 */
        NO_COINS,
        /** Refuse: the target already holds a live recommendation. / 拒绝：目标已有一条有效推荐。 */
        TARGET_BUSY,
        /** Refuse: no task type is left to add for the target. / 拒绝：目标已没有可添加的任务类型。 */
        TARGET_FULL
    }

    /**
     * Pure gate for {@code sparkwitch:use_planner_recommend}, in Tono Hanna's / 泽渡可可's order: role, round, control
     * and readiness refuse silently; then Fear; then the target, the coins, the target's live recommendation and the
     * target's free task types, each with its own line. A refused request never charges and never starts the cooldown.
     * {@code sparkwitch:use_planner_recommend} 的纯判定，顺序与远野汉娜、泽渡可可相同：职业、回合、受控与就绪静默拒绝；其后是
     * 恐惧；再依次是目标、金币、目标的有效推荐与目标的空闲任务类型，各有提示。被拒绝的请求从不扣钱、也不开始冷却。
     */
    public static RecommendVerdict recommendVerdict(boolean planner, boolean roundActive, boolean aliveParticipant,
                                                    boolean controlled, boolean ready, boolean feared,
                                                    boolean targetValid, boolean hasCoins, boolean targetBusy,
                                                    boolean targetHasFreeType) {
        if (!planner || !roundActive || !aliveParticipant || controlled || !ready) {
            return RecommendVerdict.REFUSE;
        }
        if (feared) {
            return RecommendVerdict.FEARED;
        }
        if (!targetValid) {
            return RecommendVerdict.NO_TARGET;
        }
        if (!hasCoins) {
            return RecommendVerdict.NO_COINS;
        }
        if (targetBusy) {
            return RecommendVerdict.TARGET_BUSY;
        }
        return targetHasFreeType ? RecommendVerdict.USE : RecommendVerdict.TARGET_FULL;
    }

    /**
     * Whether the requested target may receive the recommendation. The planner themselves only while
     * {@link #SELF_RECOMMEND_ALLOWED}; anyone else must be found in the planner's world, a living, present round
     * participant, within {@link #REACH} and in sight.
     * 请求的目标能否接受推荐。规划大师本人仅在 {@link #SELF_RECOMMEND_ALLOWED} 时可以；其他人必须在规划大师所在世界中找到、是在场
     * 的存活参与者、位于 {@link #REACH} 内且在视线内。
     */
    public static boolean validTarget(boolean self, boolean found, boolean targetable, boolean inReach,
                                      boolean inSight) {
        if (self) {
            return SELF_RECOMMEND_ALLOWED;
        }
        return found && targetable && inReach && inSight;
    }

    /** Within {@link #REACH} of the eyes, measured to the target's box. / 从眼睛到目标碰撞箱的距离不超过 {@link #REACH}。 */
    public static boolean withinReach(double squaredDistanceToBox) {
        return squaredDistanceToBox >= 0.0D && squaredDistanceToBox <= REACH * REACH;
    }

    /** The task types not in {@code held}, in Wathe's enum order. / 不在 {@code held} 中的任务类型，按 Wathe 枚举顺序。 */
    public static List<PlayerMoodComponent.Task> absentTypes(Collection<PlayerMoodComponent.Task> held) {
        List<PlayerMoodComponent.Task> absent = new ArrayList<>(TASK_TYPES.length);
        for (PlayerMoodComponent.Task type : TASK_TYPES) {
            if (!held.contains(type)) {
                absent.add(type);
            }
        }
        return absent;
    }

    /**
     * A uniform pick among {@code free}: {@code bound.applyAsInt(n)} returns a roll in {@code [0, n)}; null when
     * nothing is free.
     * 在 {@code free} 中均匀抽取：{@code bound.applyAsInt(n)} 返回 {@code [0, n)} 内的随机数；没有空闲类型时为 null。
     */
    public static @Nullable PlayerMoodComponent.Task pickType(List<PlayerMoodComponent.Task> free,
                                                              IntUnaryOperator bound) {
        if (free.isEmpty()) {
            return null;
        }
        return free.get(Math.floorMod(bound.applyAsInt(free.size()), free.size()));
    }

    /**
     * Task slip: {@code count} distinct task types drawn uniformly from all four (a partial Fisher-Yates shuffle with
     * {@code bound.applyAsInt(n)} in {@code [0, n)}); the old types may come back.
     * 任务单：从全部四种类型中均匀抽取 {@code count} 个互不相同的类型（用 {@code bound.applyAsInt(n)} 取 {@code [0, n)} 做部分
     * Fisher-Yates 洗牌）；原有类型可能再次出现。
     */
    public static List<PlayerMoodComponent.Task> rerollTypes(int count, IntUnaryOperator bound) {
        PlayerMoodComponent.Task[] pool = TASK_TYPES.clone();
        int draws = Math.clamp(count, 0, pool.length);
        List<PlayerMoodComponent.Task> drawn = new ArrayList<>(draws);
        for (int i = 0; i < draws; i++) {
            int pick = i + Math.floorMod(bound.applyAsInt(pool.length - i), pool.length - i);
            PlayerMoodComponent.Task swap = pool[i];
            pool[i] = pool[pick];
            pool[pick] = swap;
            drawn.add(pool[i]);
        }
        return drawn;
    }

    /** Coins a completed recommendation pays its target. / 完成推荐时目标获得的金币。 */
    public static int rewardCoins(@Nullable Faction faction) {
        return faction == Faction.CIVILIAN ? REWARD_COINS_CIVILIAN : REWARD_COINS_OTHER;
    }

    /** Coins the planner earns for completing a task (Q1). / 规划大师完成一个任务获得的金币（Q1）。 */
    public static int ownTaskCoins(boolean recommendedToSelf) {
        return OWN_TASK_COINS_FOR_EVERY_TASK || recommendedToSelf ? OWN_TASK_COINS : 0;
    }

    /**
     * A recommendation lives on the ticks {@code [start, endTick)}; {@code endTick <= 0} means none.
     * 推荐在 {@code [开始, endTick)} 这些刻内有效；{@code endTick <= 0} 表示没有推荐。
     */
    public static boolean isExpired(long now, long endTick) {
        return endTick <= 0L || now >= endTick;
    }

    /** What the world tick does with a recommendation marker. / 世界刻如何处理推荐标记。 */
    public enum MarkerAction {
        /** Keep it. / 保留。 */
        KEEP,
        /** Drop the marker only: the task is gone already. / 只清除标记：任务已不在。 */
        CLEAR,
        /** Drop the marker and remove its task silently (no pay, no TaskComplete). / 清除标记并静默移除其任务（不结算、不触发 TaskComplete）。 */
        DROP
    }

    /**
     * Marker lifecycle: a task completed, rerolled or reset elsewhere only clears the marker; an expired one, or one
     * on a player who is no longer a living round participant, also loses its task, so an unmarked leftover never
     * starts draining (the Bell Ringer Echo's silent-drop precedent).
     * 标记生命周期：任务已在别处完成、被重排或被重置时只清除标记；已过期，或其持有者不再是存活的局内参与者时，连同任务一起移除，
     * 因此不会留下一个开始消耗理智的无标记任务（沿用敲钟人回响的静默清除先例）。
     */
    public static MarkerAction markerAction(boolean participant, boolean typeHeld, boolean expired) {
        if (!typeHeld) {
            return MarkerAction.CLEAR;
        }
        return participant && !expired ? MarkerAction.KEEP : MarkerAction.DROP;
    }

    /**
     * Wathe's drain operand {@code tasks.size()}, possibly already changed by other handlers on the same read: one less
     * while the marked task is held. Skipped for a Noa-locked player, whose handler already removes every real task.
     * Wathe 下降操作数 {@code tasks.size()}，可能已被同一读取上的其他处理器改动：持有被标记的任务时减一。诺亚锁定的玩家跳过，
     * 因为她的处理器已减去全部真实任务。
     */
    public static int drainingTaskCount(int drainingTasks, boolean recommendationHeld, boolean noaLocked) {
        if (!recommendationHeld || noaLocked) {
            return drainingTasks;
        }
        return Math.max(0, drainingTasks - 1);
    }

    /**
     * Seconds the planner HUD shows until Wathe deals the next task: rounded up, or -1 when nothing is published.
     * 规划大师 HUD 显示的距 Wathe 发下一个任务的秒数：向上取整；不发布时为 -1。
     */
    public static int nextTaskSeconds(int timerTicks, boolean publish) {
        return publish ? TickTime.ceilSeconds(timerTicks) : -1;
    }

    /** What the server does with a task slip. / 服务端如何处理一次任务单使用。 */
    public enum SlipVerdict {
        /** Reroll. / 重排。 */
        USE,
        /** Refuse silently (round, control). / 静默拒绝（回合、受控）。 */
        REFUSE,
        /** Refuse with a line: no task can be rerolled; the slip is kept. / 提示拒绝：没有可重排的任务；任务单保留。 */
        NO_TASKS
    }

    /**
     * Pure gate for the task slip. {@code locked}: the target's tasks are held by another feature (a Bell Ringer
     * Echo), which reads as having nothing to reroll.
     * 任务单的纯判定。{@code locked}：目标的任务被其他功能占用（敲钟人回响），视为没有可重排的任务。
     */
    public static SlipVerdict slipVerdict(boolean roundActive, boolean aliveParticipant, boolean controlled,
                                          int targetTasks, boolean locked) {
        if (!roundActive || !aliveParticipant || controlled) {
            return SlipVerdict.REFUSE;
        }
        return targetTasks > 0 && !locked ? SlipVerdict.USE : SlipVerdict.NO_TASKS;
    }
}
