package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 探索者 Discoverer — passenger role built around open-air travel.
 * <p>Owner spec: Speed II in the open air, −25% stamina drain, no passive income (50 coins per finished task),
 * and a shop selling a spyglass and a seven-durability conductor key.
 * 探索者——围绕露天行走设计的乘客身份。
 * 所有者设定：露天时获得速度 II、体力消耗 −25%、没有被动收入（每完成一个真实任务 +50 金币），
 * 商店出售望远镜与七点耐久的乘务员钥匙。
 */
public final class DiscovererRules {

    public static final Identifier ROLE_ID = SparkWitch.id("discoverer");

    /** Owner-specified identity colour. 所有者指定的身份色。 */
    public static final int COLOR = 0xAF9042;

    /**
     * Speed II while Wathe counts the player as standing in the open air.
     * <p>Wathe's own open-air test is {@code Wathe#isSkyVisibleAdjacent} — the one its outside task and its
     * inside/outside ambience use — so the role reuses it instead of a plain sky-visibility check.
     * 满足 Wathe 露天判定时的速度 II。
     * Wathe 自己的露天判定是 {@code Wathe#isSkyVisibleAdjacent}（它的“在外面”任务与室内/室外环境音都用它），
     * 本身份直接复用该判定，而不是单纯判断头顶能否看见天空。
     */
    public static final int OPEN_AIR_SPEED_AMPLIFIER = 1;

    /** Effect duration. Longer than the re-check interval so the icon never blinks. 效果时长；比重检间隔长，图标才不会闪。 */
    public static final int OPEN_AIR_SPEED_DURATION_TICKS = 60;

    /** How often the open-air condition is re-checked. 露天判定的重检间隔。 */
    public static final int OPEN_AIR_CHECK_INTERVAL_TICKS = 10;

    /** Remaining duration below which the effect is refreshed even if the amplifier already matches. 剩余时长低于此值就续一次。 */
    public static final int OPEN_AIR_SPEED_REFRESH_BELOW_TICKS = 25;

    /** Fraction of stamina drain removed. 削减的体力消耗比例。 */
    public static final double STAMINA_DRAIN_REDUCTION = 0.25D;

    /**
     * Stamina cap multiplier that produces a 25% slower drain.
     * <p>Wathe measures stamina as a cap on how long you may sprint, so drain scales as 1/cap. A 25% lower drain is
     * therefore a 4/3 higher cap (1 / 0.75), not 1.25.
     * 让体力消耗慢 25% 所需的上限倍率。
     * Wathe 用“能跑多久”的上限来表达体力，消耗与上限成反比；
     * 因此消耗降低 25% 对应上限变为 4/3（1 / 0.75），而不是 1.25。
     */
    public static final double STAMINA_CAP_MULTIPLIER = 1.0D / (1.0D - STAMINA_DRAIN_REDUCTION);

    /** Coins granted per finished task; the role has no passive income. 每完成一个任务给的金币；该身份没有被动收入。 */
    public static final int TASK_REWARD = 50;

    /** Spyglass price in the shop. 商店里望远镜的价格。 */
    public static final int SPYGLASS_PRICE = 150;

    /** Conductor key price in the shop. 商店里乘务员钥匙的价格。 */
    public static final int CONDUCTOR_KEY_PRICE = 175;

    /** How many conductor keys one shop can sell per round. 商店每局最多卖几把乘务员钥匙。 */
    public static final int CONDUCTOR_KEY_SHOP_STOCK = 2;

    /** Shop item id of the conductor key. 乘务员钥匙的物品 id。 */
    public static final Identifier CONDUCTOR_KEY_ID = SparkWitch.id("conductor_key");

    /** Durability the conductor key ships with. 乘务员钥匙的初始耐久。 */
    public static final int CONDUCTOR_KEY_DURABILITY = 7;

    /** Durability one train-door opening costs. 开一次列车门消耗的耐久。 */
    /** 开被房间钥匙锁住的小木门（Wathe 的 {@code DoorType.SMALL_DOOR} + requiresKey）。 */
    public static final int CONDUCTOR_KEY_ROOM_DOOR_COST = 1;
    /** 开列车门（Wathe 的 {@code DoorType.TRAIN_DOOR}）。 */
    public static final int CONDUCTOR_KEY_TRAIN_DOOR_COST = 3;

    /** Cooldown after opening a door with the key. 用钥匙开门后的冷却。 */
    public static final int CONDUCTOR_KEY_COOLDOWN_TICKS = 5 * 20;

    private DiscovererRules() {
    }
}
