package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Knight rules: a civilian police variant with the Judge's task economy. / 骑士规则：使用法官任务经济的平民警察变体。 */
public final class KnightRules {
    public static final Identifier ROLE_ID = SparkWitch.id("knight");
    /** Very light warm yellow (owner, 2026-10-30). / 很浅的暖黄色（所有者，2026-10-30）。 */
    public static final int ROLE_COLOR = 0xFFE9B0;
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    /** Flat derringer cooldown in ticks, immune to trait modifiers. / 德林加固定冷却（刻），不受词条修正影响。 */
    public static final int DERRINGER_COOLDOWN_TICKS = 20;
    /** Money paid for killing a non-good player (or an Impostor). / 击杀非好人（或内鬼）获得的金钱。 */
    public static final int KILL_REWARD = 100;
    /** Sanity cost of a wrong derringer kill: the whole 0..1 mood bar (100 points). / 德林加误杀的理智代价：整条 0..1 理智（100 点）。 */
    public static final float WRONG_KILL_MOOD_COST = 1.0F;

    private KnightRules() {
    }

    public static boolean isKnight(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
