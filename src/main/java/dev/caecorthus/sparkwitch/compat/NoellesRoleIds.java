package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Weak role-id bridge for narrow NoellesRoles compatibility that remains owned by SparkWitch.
 * SparkWitch 保留的 NoellesRoles 弱身份桥，只覆盖明确的跨模组交互。
 */
public final class NoellesRoleIds {
    public static final String NAMESPACE = "noellesroles";
    public static final Identifier PHANTOM = Identifier.of(NAMESPACE, "phantom");
    public static final Identifier SHADOW_JESTER = Identifier.of(NAMESPACE, "shadow_jester");
    public static final Identifier UNDERCOVER = Identifier.of(NAMESPACE, "undercover");
    public static final Identifier VOODOO_CURSE_DEATH_REASON = Identifier.of(NAMESPACE, "voodoo");

    private NoellesRoleIds() {
    }

    public static boolean isShadowJester(@Nullable Role role) {
        return hasId(role, SHADOW_JESTER);
    }

    public static boolean isPhantom(@Nullable Role role) {
        return hasId(role, PHANTOM);
    }

    public static boolean isUndercover(@Nullable Role role) {
        return hasId(role, UNDERCOVER);
    }

    public static boolean hasId(@Nullable Role role, Identifier id) {
        return role != null && id.equals(role.identifier());
    }
}
