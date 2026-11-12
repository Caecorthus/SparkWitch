package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 泽渡可可 Sawatari Koko — neutral streamer, colour {@code FFA000}.
 * <p>Owner spec: 60 mana to go live for 20 s (everyone sees her, she sees everyone), 10 traffic per second; the second
 * key streams a corpse for 50 s (100 traffic); one shared cooldown. 2 mana per second, 15 mana and 50 coins per fake
 * task. 1000 traffic grants Speed III; 1500 traffic while live wins neutrally.
 * 泽渡可可——中立直播主，身份色 {@code FFA000}。
 * <p>所有者规格：60 魔力开播 20 秒（别人看得见她，她也能透视所有人），每秒 10 流量；第二技能键对准尸体开播 50 秒
 * （+100 流量）；两者共享冷却。每秒 2 魔力，假任务 +15 魔力 +50 金币。1000 流量获得速度 III；直播中 1500 流量中立胜利。
 */
public final class KokoRules {
    public static final Identifier ROLE_ID = SparkWitch.id("koko");
    public static final Identifier STREAM_SKILL_ID = SparkWitch.id("koko_stream");
    public static final Identifier CORPSE_SKILL_ID = SparkWitch.id("koko_corpse_stream");
    public static final Identifier LIGHTSTICK_ID = SparkWitch.id("koko_lightstick");
    public static final Identifier KNIFE_ID = SparkWitch.id("koko_knife");

    public static final int COLOR = 0xFFA000;
    public static final int OUTLINE_COLOR = 0xFFFFA000;

    public static final int STREAM_MANA = 60;
    public static final int STREAM_TICKS = 20 * 20;
    public static final int STREAM_TRAFFIC_PER_SECOND = 10;
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int COOLDOWN_TICKS = 45 * 20;

    public static final int CORPSE_HIGHLIGHT_TICKS = 50 * 20;
    public static final int CORPSE_TRAFFIC = 100;
    public static final double CORPSE_RANGE = 10.0D;
    public static final int CORPSE_COOLDOWN_TICKS = 45 * 20;

    public static final int MANA_PER_SECOND = 2;
    public static final int FAKE_TASK_MANA = 15;
    public static final int FAKE_TASK_COINS = 50;
    public static final int NATURAL_COINS = 15;
    /** Owner 2026-11-12: the store caps at 120, not Wathe's default 100. / 所有者 2026-11-12：储魔上限 120，不再是 Wathe 默认的 100。 */
    public static final int MAX_MANA = 120;

    public static final int SPEED_TRAFFIC = 1000;
    public static final int WIN_TRAFFIC = 1500;
    public static final int KILL_TRAFFIC = 100;
    public static final int SELF_HIGHLIGHT_TICKS = 10 * 20;
    public static final int KILL_HIGHLIGHT_TICKS = 50 * 20;

    public static final int LIGHTSTICK_BUFF_TICKS = 10 * 20;
    /** Owner 2026-11-12: one lightstick every 30 s. / 所有者 2026-11-12：应援棒每 30 秒一次。 */
    public static final int LIGHTSTICK_COOLDOWN_TICKS = 30 * 20;
    public static final int KNIFE_PRICE = 200;
    public static final int LIGHTSTICK_PRICE = 100;
    public static final int LOCKPICK_PRICE = 50;

    /** Pure role test, usable from the mana rules without loading the runtime service. / 纯职业判定，供魔力规则使用而不加载运行期服务。 */
    public static boolean isKoko(dev.doctor4t.wathe.api.Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    private KokoRules() {
    }
}
