package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

public final class EmmaRules {
    public static final Identifier ROLE_ID = SparkWitch.id("emma");
    public static final Identifier SKILL_ID = SparkWitch.id("emma_factor");
    public static final int COLOR = 0xF29BC3;
    public static final int MANA_COST = 100;
    public static final int COOLDOWN_TICKS = 400;
    public static final int BACKLASH_TICKS = 400;
    public static final double TARGET_RANGE = dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting.RANGE;

    private EmmaRules() { }
    public static boolean isEmma(Role role) { return role != null && ROLE_ID.equals(role.identifier()); }
}
