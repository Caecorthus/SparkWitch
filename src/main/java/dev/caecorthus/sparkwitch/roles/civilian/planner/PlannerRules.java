package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 规划大师 / Planner master: a passenger who assigns one recommended task to a marked player.
 * 规划大师：乘客阵营，可给指定玩家分配一项推荐任务。
 */
public final class PlannerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("planner_master");
    public static final int COLOR = 0xE8C46A;

    /** Cost charged when the recommendation actually fires. 技能发动时扣除的金币。 */
    public static final int SKILL_COST = 25;
    /** Cooldown before the first use. 首次使用前的初始冷却。 */
    public static final int INITIAL_COOLDOWN_TICKS = 900;
    /** Cooldown between later uses. 后续冷却。 */
    public static final int COOLDOWN_TICKS = 1400;
    /** How long a recommended task stays before it expires. 推荐任务的存在时长。 */
    public static final int TASK_LIFETIME_TICKS = 1200;
    /** Coins granted on completion for passengers. 乘客完成奖励。 */
    public static final int REWARD_COINS_CIVILIAN = 50;
    /** Coins granted on completion for killers and neutrals. 杀手与中立的完成奖励。 */
    public static final int REWARD_COINS_OTHER = 25;
    /** Mood restored on completion, as a fraction of the bar. 完成时恢复的理智比例。 */
    public static final float REWARD_MOOD = 0.75F;

    /** Extra money the planner earns for finishing their OWN task. 规划大师完成自己任务时额外获得的金币。 */
    public static final int OWN_TASK_REWARD = 75;
    /** Shop price of the task slip. 任务单的商店价格。 */
    public static final int SLIP_PRICE = 50;

    private PlannerRules() {
    }
}
