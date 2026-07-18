package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Pure identity and tuning for Kidnapper. / 绑架者的纯身份与数值规则。 */
public final class KidnapperRules {
    public static final Identifier ROLE_ID = SparkWitch.id("kidnapper");
    public static final Identifier DRAG_BODY_SKILL_ID = SparkWitch.id("kidnapper_drag_body");
    public static final Identifier SPEED_MODIFIER_ID = SparkWitch.id("kidnapper_drag_speed");
    public static final int COLOR = 0x9B59B6;
    public static final double TARGET_RANGE = 2.0D;
    public static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;
    public static final double FOLLOW_DISTANCE = 1.0D;
    public static final double THROW_SPEED = 0.8D;
    public static final double THROW_MIN_UPWARD_VELOCITY = 0.2D;
    public static final double SPEED_MULTIPLIER = 0.8D;
    public static final double SPEED_MODIFIER_AMOUNT = -0.2D;

    private KidnapperRules() {
    }

    public static boolean isKidnapper(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
