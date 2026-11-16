package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

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

    private PlannerRules() {
    }

    /** Raw-role check (never a disguise). / 原始身份判断（不看伪装）。 */
    public static boolean isPlanner(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
