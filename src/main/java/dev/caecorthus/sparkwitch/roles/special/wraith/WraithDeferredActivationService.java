package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Activates confirmed deaths only after every death listener has finished.
 * 仅在全部死亡监听器结束后激活已确认的冤魂死亡。
 */
public final class WraithDeferredActivationService {
    private static final Map<UUID, PendingDeath> PENDING = new HashMap<>();
    private static boolean registered;

    private WraithDeferredActivationService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(WraithDeferredActivationService::finishActivations);
    }

    static void queueConfirmedDeath(
            ServerPlayerEntity victim,
            Identifier deathReason,
            WraithDeathSnapshot snapshot
    ) {
        PENDING.put(victim.getUuid(), new PendingDeath(deathReason, snapshot));
    }

    static void clearPlayer(ServerPlayerEntity player) {
        PENDING.remove(player.getUuid());
    }

    static void clearAll() {
        PENDING.clear();
    }

    private static void finishActivations(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(PENDING.keySet())) {
            PendingDeath pending = PENDING.remove(uuid);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(uuid);
            if (pending != null && victim != null) {
                activate(victim, pending.deathReason(), pending.snapshot());
            }
        }
    }

    private static boolean activate(
            ServerPlayerEntity victim,
            Identifier deathReason,
            WraithDeathSnapshot snapshot
    ) {
        if (SparkTraitsWraithBridge.didLastStandTriggerSince(victim, snapshot.lastStandTriggeredBefore())) {
            return false;
        }
        WraithPlayerComponent wraithComponent = WraithPlayerComponent.KEY.get(victim);
        if (wraithComponent.isActive()
                || !WraithRules.isEligibleDeath(snapshot.originalFaction(), deathReason)
                || !WraithRoundQuotaService.hasCapacity(victim.getServerWorld())
                || !WraithRules.passesChance(victim.getRandom().nextDouble())
                || !WraithRoundQuotaService.tryConsume(victim.getServerWorld(), victim.getUuid())) {
            return false;
        }

        WraithBodyService.ensureDeathBody(victim, deathReason, snapshot.deathGameTime());
        SparkTraitsWraithBridge.restore(victim, snapshot.traitSnapshot());
        wraithComponent.activate(snapshot.alignment());
        WraithRoleTransitionService.transition(victim, SparkWitchRoles.wraith());
        WraithSessionService.activatePlayer(victim);
        WraithTaskService.restoreForActivation(victim, snapshot.taskSnapshot());
        victim.closeHandledScreen();
        victim.getInventory().clear();
        victim.currentScreenHandler.sendContentUpdates();
        return true;
    }

    private record PendingDeath(Identifier deathReason, WraithDeathSnapshot snapshot) {
    }
}
