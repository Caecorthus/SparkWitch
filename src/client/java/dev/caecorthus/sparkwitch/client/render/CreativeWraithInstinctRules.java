package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.player.PlayerEntity;

/** Narrow exception that exposes active Wraiths only to creative instinct highlighting. */
public final class CreativeWraithInstinctRules {
    private CreativeWraithInstinctRules() {
    }

    public static boolean shouldReveal(PlayerEntity viewer, PlayerEntity target) {
        return shouldReveal(
                SparkWitchServerConnection.isConfirmedServer(),
                viewer != null && viewer.isCreative(),
                viewer != null && viewer.isSpectator(),
                viewer != null && target != null && viewer.getUuid().equals(target.getUuid()),
                target != null && WraithClientState.isActive(target),
                WatheClient.instinctKeybind != null && WatheClient.instinctKeybind.isPressed()
        );
    }

    public static boolean shouldReveal(
            boolean confirmedServer,
            boolean creativeViewer,
            boolean spectatorViewer,
            boolean samePlayer,
            boolean activeWraithTarget,
            boolean instinctKeyPressed
    ) {
        return confirmedServer
                && creativeViewer
                && !spectatorViewer
                && !samePlayer
                && activeWraithTarget
                && instinctKeyPressed;
    }
}
