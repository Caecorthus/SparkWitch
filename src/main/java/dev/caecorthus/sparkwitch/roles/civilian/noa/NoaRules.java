package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 城崎诺亚 Noa Jogasaki — a passenger painter with her own sanity rules. Her sanity never drains on its own and tasks
 * never restore it (Wathe's own drain and task gain are zeroed for her; every other sanity effect still lands), and a
 * -100% breakdown still kills her. The ability key paints: one of two tables depending on whether her sanity is above
 * 25%. Her exact sanity value is never shown.
 * 城崎诺亚——拥有自己一套理智规则的乘客画家。理智不会自然下降、做任务也不回复（只把 Wathe 自身的流失与任务回复对她清零，
 * 其他理智效果照常生效），理智到 -100% 时精神崩溃照样会杀死她。按技能键作画，按理智是否高于 25% 走两张不同的结果表。
 * 从不显示她的具体理智值。
 */
public final class NoaRules {

    public static final Identifier ROLE_ID = SparkWitch.id("noa");

    /** Identity colour 9CC8FC. / 身份色 9CC8FC。 */
    public static final int COLOR = 0x9CC8FC;

    /** Forced-cooldown store id for the painting. / 作画的强制冷却存储 id。 */
    public static final Identifier PAINT_SKILL_ID = SparkWitch.id("noa_paint");

    /**
     * Stable item contract: the big dog portrait a high-sanity painting can produce. Carried by a living Noa (any
     * inventory slot or the off hand), it punishes anyone else who kills within {@link #DOG_RADIUS} of her.
     * 稳定物品契约：高理智作画可能画出的大狗画像。存活的诺亚携带它（任意背包格或副手）时，在她 {@link #DOG_RADIUS}
     * 格内杀人的其他人会受到惩罚。
     */
    public static final Identifier DOG_PORTRAIT_ITEM_ID = SparkWitch.id("big_dog_portrait");

    /** Painting: 50 s at round start, 60 s after each painting. / 作画：开局 50 秒，每次作画后 60 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 50 * 20;
    public static final int COOLDOWN_TICKS = 60 * 20;

    /** Sanity above this paints from the high table, otherwise the low one. / 理智高于此值走高理智表，否则走低理智表。 */
    public static final float LOW_SANITY_THRESHOLD = 0.25F;

    /** Each shop row moves her sanity by 25%. / 每行商店改变 25% 理智。 */
    public static final float SHOP_SANITY_STEP = 0.25F;
    public static final int SHOP_PRICE = 50;

    /** Income: tasks pay 50, and 5 every 10 s on Wathe's passive-money ticks. / 收入：任务 50 金币，另在 Wathe 被动金币刻每 10 秒 +5。 */
    public static final int TASK_COINS = 50;
    public static final int PASSIVE_COINS = 5;
    public static final int PASSIVE_INTERVAL_TICKS = 10 * 20;

    /** The dog's watch radius and punishment length. / 大狗的监视半径与惩罚时长。 */
    public static final double DOG_RADIUS = 10.0D;
    public static final int DOG_PUNISH_TICKS = 5 * 20;

    /** NoellesRoles' own whiskey duration (400 ticks at multiplier 1). / NoellesRoles 威士忌自身的时长（倍率 1 时 400 刻）。 */
    public static final int WHISKEY_SHIELD_TICKS = 400;

    /** Painted Speed lasts 10 s. / 作画得到的速度持续 10 秒。 */
    public static final int SPEED_TICKS = 10 * 20;

    private NoaRules() {
    }

    public static boolean isNoa(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** Whether the high table applies. / 是否走高理智表。 */
    public static boolean paintsHigh(float sanity) {
        return sanity > LOW_SANITY_THRESHOLD;
    }

    /** Passive coins due at world time {@code worldTime} (Wathe's ticker alignment). / 世界时间对应的被动金币（与 Wathe 计时器对齐）。 */
    public static int passiveIncomeAt(long worldTime) {
        return worldTime % PASSIVE_INTERVAL_TICKS == 0 ? PASSIVE_COINS : 0;
    }

    /**
     * Sanity after one shop purchase, or {@code null} when the bar is already at that end (the row then refuses and
     * costs nothing). Wathe clamps mood to -1..1.
     * 购买一次后的理智；已在该端点时为 {@code null}（该行拒绝且不扣钱）。Wathe 把理智限制在 -1..1。
     */
    public static @Nullable Float shopSanity(float sanity, boolean restore) {
        float next = Math.clamp(sanity + (restore ? SHOP_SANITY_STEP : -SHOP_SANITY_STEP), -1.0F, 1.0F);
        return next == sanity ? null : next;
    }
}
