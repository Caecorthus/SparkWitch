package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Prophet identity, passive Death Sense timing, and Prophecy skill constants.
 * 先知身份、被动「死亡感知」计时与「预言」技能常量。
 */
public final class ProphetRules {
    public static final Identifier ROLE_ID = SparkWitch.id("prophet");
    public static final Identifier PROPHECY_ID = SparkWitch.id("prophecy");
    public static final int ROLE_COLOR = 0xD4AF37;
    public static final int CORPSE_HIGHLIGHT_COLOR = 0xFF3030;
    public static final int CORPSE_HIGHLIGHT_PRIORITY = 90;
    /** Death Sense period; the first pulse fires this long after the role is assigned. / 死亡感知周期；拿到职业后经过同样时长触发首次感知。 */
    public static final int SENSE_INTERVAL_TICKS = 1200;
    public static final int PROPHECY_INITIAL_COOLDOWN_TICKS = 1200;
    public static final int PROPHECY_COOLDOWN_TICKS = 600;
    public static final int PROPHECY_COIN_COST = 50;

    private ProphetRules() {
    }

    public static boolean isProphet(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
