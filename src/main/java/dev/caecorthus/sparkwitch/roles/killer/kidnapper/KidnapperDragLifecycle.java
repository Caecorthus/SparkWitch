package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Owns Kidnapper drag cleanup wiring. / 只负责绑架者拖尸运行态清理接线。 */
public final class KidnapperDragLifecycle {
    private static boolean registered;

    private KidnapperDragLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerTickEvents.END_WORLD_TICK.register(world ->
                world.getPlayers().forEach(KidnapperDragService::reconcile));
        KillPlayer.AFTER.register((victim, killer, deathReason) -> KidnapperDragService.release(victim));
        ResetPlayer.EVENT.register(KidnapperDragService::release);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                KidnapperDragService.release(serverPlayer);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                KidnapperDragService.release(handler.getPlayer()));
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> clearWorld(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> clearWorld(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                server.getWorlds().forEach(KidnapperDragLifecycle::clearWorld));
    }

    private static void clearWorld(net.minecraft.world.World world) {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getPlayers().forEach(KidnapperDragService::release);
        }
    }
}
