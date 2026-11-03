package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 橘雪莉 Tachibana Sherry — passenger detective: keeps real sanity at 1.5× stamina, earns coins and mana naturally,
 * and may spend 200 coins to deduce a killer from a corpse's body.
 * <p>Owner spec 2026-11-02 (image one). Passenger faction, identity colour {@code #65D0EE}.
 * 橘雪莉——乘客阵营的侦探：真实理智、1.5 倍体力，能自然获得金币与魔力，
 * 并可花费 200 金币从尸体上推演出凶手。
 * <p>所有者规格 2026-11-02（图一）。乘客阵营，身份色 {@code #65D0EE}。
 */
public final class SherryRules {

    public static final Identifier ROLE_ID = SparkWitch.id("sherry");

    /** Identity colour from the spec: 65D0EE. 规格给定的身份色：65D0EE。 */
    public static final int COLOR = 0x65D0EE;

    /** Round-start cooldown for this role's skill, in ticks (45 s, owner 2026-11-02).
     * 该身份技能的回合初始冷却（45 秒，所有者 2026-11-02）。 */
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;

    /** Passive coins/mana income per payout tick. 被动金币与魔力收入。 */
    public static final int PASSIVE_COINS = 5;
    public static final int PASSIVE_MANA = 2;
    public static final int PASSIVE_INTERVAL_TICKS = 200;

    /** Task payout: 50 coins and 10 mana. 任务报酬：50 金币与 10 魔力。 */
    public static final int TASK_COINS = 50;
    public static final int TASK_MANA = 10;

    /** Below this light level the sanity drain is halved. 亮度低于此值时理智下降减半。 */
    public static final int DIM_LIGHT_LEVEL = 10;

    /** Deduction costs 200 coins and reaches 60 blocks. 推演花费 200 金币，射程 60 格。 */
    public static final int DEDUCTION_COST = 200;
    public static final double DEDUCTION_RANGE = 60.0D;

    /** Deduction cooldown in ticks (30 s). 推演冷却（30 秒）。 */
    public static final int DEDUCTION_COOLDOWN_TICKS = 30 * 20;

    /** Wrong-target penalty: 35% sanity, Slowness and Blindness for 5 s. 误判惩罚：35% 理智、缓慢与失明 5 秒。 */
    public static final float WRONG_TARGET_MOOD_LOSS = 0.35F;
    public static final int WRONG_TARGET_EFFECT_TICKS = 5 * 20;

    /** Badge price and per-round stock. 徽章价格与每局库存。 */
    public static final int BADGE_PRICE = 50;
    public static final int BADGE_STOCK = 2;

    /** Badge holder glow colour: orange. 徽章持有者的透视色：橘黄。 */
    public static final int BADGE_GLOW = 0xFFA500;

    /** Emma's own glow colour while both are in the round. 樱羽艾玛在场的联动色。 */
    public static final int EMMA_GLOW = 0xFF69B4;

    private SherryRules() {
    }
}
