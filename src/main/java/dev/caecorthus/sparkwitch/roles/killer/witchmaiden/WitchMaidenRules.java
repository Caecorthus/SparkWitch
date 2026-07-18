package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable Witch Maiden ids, tuning, and pure kill gates.
 * 巫女拥有的稳定标识、数值与纯击杀判断。
 */
public final class WitchMaidenRules {
    public static final Identifier ROLE_ID = SparkWitch.id("witch_maiden");
    public static final Identifier FOCUSED_FOOTSTEPS_SKILL_ID = SparkWitch.id("focused_footsteps");
    public static final Identifier VOODOO_DEATH_REASON_ID = NoellesRoleIds.VOODOO_CURSE_DEATH_REASON;
    public static final Identifier TOFANA_DEATH_REASON_ID = SparkWitch.id("tofana_elixir");
    public static final int COLOR = 0xB04A8B;
    public static final int FOCUSED_FOOTSTEPS_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int FOCUSED_FOOTSTEPS_DURATION_TICKS = 30 * 20;
    public static final int FOCUSED_FOOTSTEPS_COOLDOWN_TICKS = 90 * 20;
    public static final int KNIFE_PRICE = 100;
    public static final int LOCKPICK_PRICE = 50;
    public static final int POISON_PRICE = 75;
    public static final int TOFANA_PRICE = 200;

    private WitchMaidenRules() {
    }

    public static boolean isWitchMaiden(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** Cancels only NoellesRoles' final Voodoo death, not its binding or countdown. / 只取消巫毒最终死亡，不改绑定与倒计时。 */
    public static boolean blocksVoodooDeath(@Nullable Role victimRole, @Nullable Identifier deathReason) {
        return isWitchMaiden(victimRole) && VOODOO_DEATH_REASON_ID.equals(deathReason);
    }

    public static boolean shouldTriggerTofana(
            boolean witchMaidenVictim,
            boolean distinctPlayerKiller,
            boolean killerPlayingAndAlive,
            boolean carriesElixir
    ) {
        return witchMaidenVictim && distinctPlayerKiller && killerPlayingAndAlive && carriesElixir;
    }
}
