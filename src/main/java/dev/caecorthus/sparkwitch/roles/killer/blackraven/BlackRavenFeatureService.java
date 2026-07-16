package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Registers Black Raven lifecycle hooks while keeping the global event owner declarative. */
public final class BlackRavenFeatureService {
    private static boolean registered;

    private BlackRavenFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackRavenShopService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            BlackRavenPerceptionPlayerComponent perception = BlackRavenPerceptionPlayerComponent.KEY.get(serverPlayer);
            if (!BlackRavenRules.isBlackRaven(role)) {
                if (perception.hasRoundState()) {
                    BlackRavenPerceptionService.clearForRoleLossOrDeath(serverPlayer);
                    BlackRavenLoadoutService.removeOwnedItems(serverPlayer);
                } else {
                    BlackRavenLoadoutService.removeLedger(serverPlayer);
                }
                return;
            }
            BlackRavenLoadoutService.assignForRole(serverPlayer, role);
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> clearDeadPlayer(victim));
        ResetPlayer.EVENT.register(BlackRavenFeatureService::clearDeadPlayer);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlackRavenMarkPlayerComponent.KEY.get(player).clear();
                if (BlackRavenRules.isBlackRaven(game.getRole(player))) {
                    BlackRavenPerceptionService.bindCurrentMatch(player);
                    BlackRavenLoadoutService.restoreLedgerIfNeeded(player);
                } else {
                    BlackRavenPerceptionPlayerComponent.KEY.get(player).clear();
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (BlackRavenPerceptionPlayerComponent.KEY.get(player).hasRoundState()) {
                    BlackRavenPerceptionService.clearForRoleLossOrDeath(player);
                }
                BlackRavenMarkPlayerComponent.KEY.get(player).clear();
                BlackRavenLoadoutService.removeOwnedItems(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (BlackRavenPerceptionPlayerComponent.KEY.get(player).isActive()) {
                BlackRavenPerceptionService.cancelForDisconnect(player);
            }
        });
    }

    private static void clearDeadPlayer(ServerPlayerEntity player) {
        if (BlackRavenPerceptionPlayerComponent.KEY.get(player).hasRoundState()) {
            BlackRavenPerceptionService.clearForRoleLossOrDeath(player);
        }
        BlackRavenMarkPlayerComponent.KEY.get(player).clear();
        if (BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            BlackRavenLoadoutService.removeOwnedItems(player);
        } else {
            BlackRavenLoadoutService.removeLedger(player);
        }
    }
}
