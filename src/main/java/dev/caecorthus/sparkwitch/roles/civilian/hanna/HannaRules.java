package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 远野汉娜 Tono Hanna — passenger who drifts.
 * <p>Owner spec 2026-11-10: identity colour {@code FFAF9F}; the ability key spends 40 mana for a 10 s float
 * (Speed II, silent, immune to instinct highlighting). Being touched during the float cancels it and starts a 10 s
 * penalty that drains 5% sanity per second. 40 s initial cooldown, 45 s afterwards.
 * 远野汉娜——会漂浮的乘客。
 * <p>所有者规格 2026-11-10：身份色 {@code FFAF9F}；按技能键消耗 40 魔力进入 10 秒浮游
 * （速度 II、静音、无法被本能透视）。浮游期间被他人交互则立刻结束，并进入 10 秒惩罚、每秒扣 5% 理智。
 * 初始冷却 40 秒，之后 45 秒。
 */
public final class HannaRules {

    public static final Identifier ROLE_ID = SparkWitch.id("hanna");
    public static final Identifier UMBRELLA_ID = SparkWitch.id("hanna_umbrella");
    public static final Identifier HANDKERCHIEF_ID = SparkWitch.id("hanna_handkerchief");

    /** Skill-panel entry so the mana bar is shown and the key runs through the skill system. */
    /** 技能面板条目，让魔力条显示、技能键走技能系统。 */
    public static final Identifier SKILL_ID = SparkWitch.id("hanna_float");

    public static final int COLOR = 0xFFAF9F;

    /** Float: 40 mana for 10 s. 浮游：40 魔力，10 秒。 */
    public static final int FLOAT_TICKS = 10 * 20;
    public static final int FLOAT_MANA = 40;
    public static final int INITIAL_COOLDOWN_TICKS = 40 * 20;
    public static final int COOLDOWN_TICKS = 45 * 20;

    /** Interrupted float: 10 s of penalty, 5% sanity per second. 被打断：10 秒惩罚，每秒 5% 理智。 */
    public static final int PENALTY_TICKS = 10 * 20;
    public static final float PENALTY_SANITY_PER_SECOND = 0.05F;

    /** Income: 15 coins every 30 s, plus task coins. 收入：每 30 秒 15 金币，外加任务金币。 */
    public static final int PASSIVE_COINS = 15;
    public static final int PASSIVE_INTERVAL_TICKS = 30 * 20;
    public static final int TASK_COINS = 50;

    /** Umbrella: 150 coins, slow falling, 25 durability, 1 per second held. 洋伞：150 金币，缓降，25 耐久，每秒 -1。 */
    public static final int UMBRELLA_PRICE = 150;
    public static final int UMBRELLA_DURABILITY = 25;

    /** Handkerchief: 50 coins, 5 s of +6% sanity per second, clears the float penalty. */
    /** 手帕：50 金币，5 秒内每秒 +6% 理智，并解除浮游惩罚。 */
    public static final int HANDKERCHIEF_PRICE = 100;
    public static final int HANDKERCHIEF_TICKS = 5 * 20;
    /** 2 uses before it wears out, and 15 s between uses. 用 2 次后损坏，每次间隔 15 秒。 */
    public static final int HANDKERCHIEF_USES = 2;
    public static final int HANDKERCHIEF_COOLDOWN_TICKS = 15 * 20;
    /** The float pool: 40 mana at deal time and never refilled. 浮游魔力池：开局 40 点，永不回复。 */
    public static final int INITIAL_MANA = 40;
    public static final float HANDKERCHIEF_SANITY_PER_SECOND = 0.06F;

    private HannaRules() {
    }
}
