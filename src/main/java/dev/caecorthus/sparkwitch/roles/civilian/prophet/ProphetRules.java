package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ProphetRules {
    public static final Identifier ROLE_ID = SparkWitch.id("prophet");
    public static final Identifier DEATH_OMEN_ID = SparkWitch.id("death_omen");
    public static final int ROLE_COLOR = 0xD4AF37;
    public static final int CORPSE_HIGHLIGHT_COLOR = 0xFF3030;
    public static final int CORPSE_HIGHLIGHT_PRIORITY = 90;
    public static final int INITIAL_COOLDOWN_TICKS = 1200;
    public static final int ACTIVE_TICKS = 400;
    public static final int POST_COOLDOWN_TICKS = 1800;

    private ProphetRules() {
    }

    public static boolean isProphet(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean shouldRecordLoadedBody(
            int currentGameTime,
            int bodyDeathGameTime,
            boolean ownerMarkedDead
    ) {
        return ownerMarkedDead && bodyDeathGameTime == currentGameTime;
    }
}
