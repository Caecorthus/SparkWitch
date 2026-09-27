package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Narrow adapter for NoellesRoles silencing ({@code SilencedPlayerComponent.isPlayerSilenced}); any failure reports
 * "not silenced" and is logged once.
 * NoellesRoles 沉默状态的窄适配器（{@code SilencedPlayerComponent.isPlayerSilenced}）；任何失败都视为“未被沉默”并只记录一次日志。
 */
public final class NoellesSilenceBridge {
    private NoellesSilenceBridge() {
    }

    public static boolean isSilenced(PlayerEntity player) {
        // TODO(WP-03a): query NR SilencedPlayerComponent; catch and log once.
        return false;
    }
}
