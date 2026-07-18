package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Clears the target-owned effect at terminal lifecycle boundaries without tracking its source.
 * 在终止生命周期边界清除目标自有的效果，不追踪施法来源。
 */
public final class FocusedFootstepsRuntime {
    private static boolean registered;

    private FocusedFootstepsRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, deathReason) -> clear(victim));
        ResetPlayer.EVENT.register(FocusedFootstepsRuntime::clear);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    clear(player);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.getPlayer()));
    }

    private static void clear(ServerPlayerEntity player) {
        if (player.removeStatusEffect(FocusedFootstepsEffects.focusedFootsteps())) {
            player.setSprinting(false);
        }
    }
}
