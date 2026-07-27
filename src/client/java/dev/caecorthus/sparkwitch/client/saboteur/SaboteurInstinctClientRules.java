package dev.caecorthus.sparkwitch.client.saboteur;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Extends Wathe's native killer-instinct gate only for the exact promoted Saboteur. / 仅为精确的晋升破坏者扩展 wathe 原生杀手本能门禁。 */
public final class SaboteurInstinctClientRules {
    private SaboteurInstinctClientRules() {
    }

    public static boolean shouldEnableNativeInstinct(
            boolean originalAllowed,
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean activeWraith,
            boolean promotedWraith,
            boolean instinctKeyPressed
    ) {
        return originalAllowed
                || confirmedServer
                && SaboteurRole.ID.equals(roleId)
                && activeWraith
                && promotedWraith
                && instinctKeyPressed;
    }
}
