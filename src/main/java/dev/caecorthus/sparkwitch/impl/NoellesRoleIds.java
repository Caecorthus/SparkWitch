package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Weak role-id bridge for NoellesRoles compatibility that remains owned by SparkWitch.
 * SparkWitch 保留的 NoellesRoles 兼容桥，只覆盖魔女专属交互。
 */
public final class NoellesRoleIds {
    public static final String NAMESPACE = "noellesroles";
    public static final Identifier SHADOW_JESTER = Identifier.of(NAMESPACE, "shadow_jester");
    public static final Identifier VOODOO_CURSE_DEATH_REASON = Identifier.of(NAMESPACE, "voodoo");

    private NoellesRoleIds() {
    }

    public static boolean isShadowJester(@Nullable Role role) {
        return hasId(role, SHADOW_JESTER);
    }

    public static boolean hasId(@Nullable Role role, Identifier id) {
        return role != null && id.equals(role.identifier());
    }
}
