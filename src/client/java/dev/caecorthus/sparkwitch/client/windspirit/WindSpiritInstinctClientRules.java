package dev.caecorthus.sparkwitch.client.windspirit;

import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRole;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Grants Wathe's native instinct visuals only to the exact promoted Wind Spirit identity. / 仅向精确的晋升风精灵身份开放 Wathe 原生本能视觉。 */
public final class WindSpiritInstinctClientRules {
    private WindSpiritInstinctClientRules() {
    }

    public static boolean hasNativeInstinctVisuals(@Nullable Identifier roleId) {
        return WindSpiritRole.ID.equals(roleId);
    }

    public static boolean shouldEnableNativeInstinct(
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean viewerPlayingAndAlive,
            boolean promotedWraith,
            boolean instinctKeyPressed
    ) {
        return confirmedServer
                && hasNativeInstinctVisuals(roleId)
                && (viewerPlayingAndAlive || promotedWraith)
                && instinctKeyPressed;
    }

    public static int resolveNativePlayerHighlightColor(
            int originalColor,
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean viewerPlayingAndAlive,
            boolean promotedWraith
    ) {
        return confirmedServer
                && hasNativeInstinctVisuals(roleId)
                && (viewerPlayingAndAlive || promotedWraith)
                ? WindSpiritRole.COLOR
                : originalColor;
    }

    public static boolean shouldUseNativeKillerHighlight(
            boolean ordinarilyKiller,
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean viewerPlayingAndAlive,
            boolean promotedWraith
    ) {
        return ordinarilyKiller
                || confirmedServer
                && hasNativeInstinctVisuals(roleId)
                && (viewerPlayingAndAlive || promotedWraith);
    }
}
