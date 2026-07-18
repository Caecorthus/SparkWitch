package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import org.jetbrains.annotations.Nullable;

/** Resolves the immutable role captured on a corpse without changing Wathe's entity class. */
public final class WraithBodyRoleResolver {
    private WraithBodyRoleResolver() {
    }

    public static @Nullable Role resolve(PlayerBodyEntity body, @Nullable Role fallback) {
        var roleId = ((WraithBodyRoleAccess) body).sparkwitch$getDeathRole();
        Role role = roleId == null ? null : WatheRoles.getRole(roleId);
        return role == null ? fallback : role;
    }
}
