package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
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
    /** Sanity cost of a wrong derringer kill: 1.0 mood (100 points; Wathe clamps mood to -1..1). / 德林加误杀的理智代价：1.0 理智（100 点；Wathe 将理智限制在 -1..1）。 */
    public static final float WRONG_KILL_MOOD_COST = 1.0F;

    private KnightRules() {
    }

    public static boolean isKnight(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * "Good" for every Knight rule (duel pick, kill reward, derringer misfire, push-off): SparkFactionAPI's effective
     * faction is CIVILIAN, which already folds Impostor out and Conscience in. Neutrals are not good (owner,
     * 2026-10-30).
     * 骑士所有规则（选决斗目标、击杀奖励、德林加误杀、推下车）的“好人”：SparkFactionAPI 有效阵营为 CIVILIAN，
     * 内鬼已排除、善良已计入。中立不算好人（所有者，2026-10-30）。
     */
    public static boolean isGoodForKnight(@Nullable PlayerEntity player, GameWorldComponent game) {
        return player != null && FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(player, game));
    }
}
