package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** SparkWitch-owned corpse snapshot contract; Wathe remains unchanged. */
public interface WraithBodyRoleAccess {
    void sparkwitch$setDeathRole(@Nullable Identifier roleId);

    @Nullable Identifier sparkwitch$getDeathRole();
}
