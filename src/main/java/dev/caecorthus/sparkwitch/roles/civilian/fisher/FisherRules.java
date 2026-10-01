package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-approved Angler (钓鱼佬) ids and numbers (docs/plans/2026-09-28-civ-fisher-decisions.md). Pure constants.
 * 所有者批准的钓鱼佬 id 与数值（见 decisions 文件）。纯常量。
 */
public final class FisherRules {
    public static final Identifier ROLE_ID = SparkWitch.id("fisher");
    public static final int COLOR = 0x2A9D8F;

    public static final Identifier FISHING_ROD_ID = SparkWitch.id("fishing_rod");
    public static final Identifier BAIT_ID = SparkWitch.id("fish_bait");
    public static final Identifier SALMON_ID = SparkWitch.id("salmon");
    public static final Identifier COD_ID = SparkWitch.id("cod");
    public static final Identifier CLOWNFISH_ID = SparkWitch.id("clownfish");
    public static final Identifier GOLDFISH_ID = SparkWitch.id("goldfish");
    public static final Identifier KEY_FISH_ID = SparkWitch.id("key_fish");
    public static final Identifier SWORDFISH_ID = SparkWitch.id("swordfish");
    public static final Identifier GLIMMERFISH_ID = SparkWitch.id("glimmerfish");

    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    public static final String BAIT_SHOP_ENTRY_ID = "fish_bait";
    public static final int BAIT_PRICE = 25;

    /** Anti-double-click guard after every cast, shown as the rod's item cooldown. / 每次抛竿后的防连点冷却。 */
    public static final int ROD_COOLDOWN_TICKS = 20;
    /** Shared by every edible fish so one click never eats two. / 所有可食用鱼共享，一次点击不会吃掉两条。 */
    public static final int FISH_USE_COOLDOWN_TICKS = 20;

    public static final float SALMON_MOOD = 0.10f;
    public static final float COD_MOOD = 0.20f;
    public static final float CLOWNFISH_MOOD = 0.30f;
    public static final float GOLDFISH_MOOD = 0.20f;
    public static final float GLIMMERFISH_MOOD = 1.0f;
    public static final int CLOWNFISH_SPEED_TICKS = 200;
    public static final int CLOWNFISH_SPEED_AMPLIFIER = 0;
    public static final int GOLDFISH_COINS = 50;

    public static final int PUFFERFISH_LIFETIME_TICKS = 200;
    public static final float PUFFERFISH_MOOD_DRAIN = 0.10f;
    public static final int PUFFERFISH_STING_COOLDOWN_TICKS = 40;

    /** 7.77 s rounded up so the window is never shorter than promised. / 7.77 秒向上取整，窗口不短于承诺。 */
    public static final int GLIMMER_WINDOW_TICKS = 156;

    /** Same feel as Wathe's knife: release after strictly more than 10 held ticks, 3 blocks. / 手感同 Wathe 刀。 */
    public static final double SWORDFISH_REACH = 3.0;
    public static final int SWORDFISH_MIN_HOLD_TICKS = 11;

    public static final int BAIT_MAX_COUNT = 16;
    public static final int FISH_MAX_COUNT = 16;

    private FisherRules() {
    }

    public static boolean isFisher(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
