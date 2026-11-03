package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 城崎诺亚 Noa Jogasaki — passenger painter with her own sanity rules.
 * <p>Owner spec 2026-11-02: identity colour {@code 9CC8FC}, her sanity bar is cyan, the exact percentage is shown
 * under it, she never loses sanity passively and tasks never restore it — but a breakdown at -100% still kills her. Painting
 * on the ability key rolls one of two tables depending on whether her sanity is above or below 25%.
 * 城崎诺亚——拥有自己一套理智规则的乘客画家。
 * <p>所有者规格 2026-11-02：身份色 {@code 9CC8FC}，理智条为青色，条下方显示具体百分比；
 * 不会自然掉理智、做任务不回理智；但理智到 -100% 时精神崩溃照样会杀死她。
 * 按技能键作画，按理智是否高于 25% 走两张不同的结果表。
 */
public final class NoaRules {

    public static final Identifier ROLE_ID = SparkWitch.id("noa");

    /** Identity colour: 9CC8FC (the gradient to FFBEE5 is a later cosmetic pass). 身份色：9CC8FC。 */
    public static final int COLOR = 0x9CC8FC;

    /** Painting: 50 s at round start, 60 s afterwards. 作画：开局 50 秒，之后 60 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 50 * 20;
    public static final int COOLDOWN_TICKS = 60 * 20;

    /** The 25% threshold that switches the result table. 切换结果表的 25% 阈值。 */
    public static final float LOW_SANITY_THRESHOLD = 0.25F;

    /** Sanity costs and gains, as fractions of the bar. 理智的扣除与回复比例。 */
    public static final float HIGH_TABLE_SANITY_LOSS = 0.25F;
    public static final float LOW_TABLE_SANITY_LOSS = 0.30F;
    public static final float SHOP_SANITY_STEP = 0.25F;

    /** Shop prices. 商店价格。 */
    public static final int SHOP_PRICE = 50;

    /** Income: tasks pay 50, and she earns 5 every 10 s passively (owner 2026-11-03). */
    /** 收入：任务 50 金币，另每 10 秒自然 +5（所有者 2026-11-03）。 */
    public static final int TASK_COINS = 50;
    public static final int PASSIVE_COINS = 5;
    public static final int PASSIVE_INTERVAL_TICKS = 10 * 20;

    /** Timer for the wolf spawn egg: whoever kills within this radius while she carries it gets punished. */
    /** 狼蛋的判定半径：携带期间在这个范围内杀人的人会被惩罚。 */
    public static final double WOLF_RADIUS = 10.0D;
    public static final int WOLF_PUNISH_TICKS = 5 * 20;

    private NoaRules() {
    }
}
