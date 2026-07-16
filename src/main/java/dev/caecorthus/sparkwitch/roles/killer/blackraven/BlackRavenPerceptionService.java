package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative Perception activation, accumulation, and cleanup. */
public final class BlackRavenPerceptionService {
    private static final int MAINTAINED_BLINDNESS_TICKS = 40;

    private BlackRavenPerceptionService() {
    }

    public static boolean activate(ServerPlayerEntity player) {
        UUID matchId = BlackRavenMatch.currentId();
        if (matchId == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        if (component.isActive()) {
            return false;
        }
        if (!matchId.equals(component.matchId())) {
            component.bindMatch(matchId);
        }
        boolean ownsBlindness = !player.hasStatusEffect(StatusEffects.BLINDNESS);
        if (!component.begin(BlackRavenRules.PERCEPTION_ACTIVE_TICKS, ownsBlindness)) {
            return false;
        }
        maintainBlindness(player);
        return true;
    }

    public static void tick(ServerPlayerEntity player, BlackRavenPerceptionPlayerComponent component) {
        UUID currentMatch = BlackRavenMatch.currentId();
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!component.isActive()) {
            if (component.hasRoundState() && !BlackRavenRules.shouldPreservePerceptionRoundState(
                    component.matchId(),
                    currentMatch,
                    BlackRavenRules.isBlackRaven(role)
            )) {
                clearForRoleLossOrDeath(player);
                BlackRavenLoadoutService.removeOwnedItems(player);
                return;
            }
            BlackRavenLoadoutService.restoreLedgerIfNeeded(player);
            return;
        }
        if (currentMatch == null || !currentMatch.equals(component.matchId())
                || !BlackRavenRules.isBlackRaven(role)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            forceEnd(player, component, true);
            return;
        }

        maintainBlindness(player);
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        for (ServerPlayerEntity target : player.getServerWorld().getPlayers()) {
            if (target == player
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || GameFunctions.isPlayerSpectatingOrCreative(target)
                    || !BlackRavenRules.isWithinPerceptionRadius(player.squaredDistanceTo(target))) {
                continue;
            }
            Role targetRole = game.getRole(target);
            if (targetRole == null) {
                continue;
            }
            component.accumulate(target.getUuid(), 1, () -> new BlackRavenIdentitySnapshot(
                    target.getUuid(),
                    target.getGameProfile().getName(),
                    targetRole.identifier().toString(),
                    targetRole.color()
            ));
        }

        int remainingTicks = component.decrementActiveTicks();
        if (remainingTicks <= 0) {
            finishNaturally(player, component);
        } else if (remainingTicks % 20 == 0) {
            component.syncOwner();
        }
        BlackRavenLoadoutService.restoreLedgerIfNeeded(player);
    }

    public static void bindCurrentMatch(ServerPlayerEntity player) {
        UUID matchId = BlackRavenMatch.currentId();
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        if (matchId == null) {
            component.clear();
            return;
        }
        component.bindMatch(matchId);
    }

    public static void clearForRoleLossOrDeath(ServerPlayerEntity player) {
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        removeOwnedBlindness(player, component);
        component.clear();
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        witch.clearDeferredCooldownState();
        witch.sync();
    }

    public static void cancelForDisconnect(ServerPlayerEntity player) {
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        forceEnd(player, component, false);
    }

    private static void finishNaturally(
            ServerPlayerEntity player,
            BlackRavenPerceptionPlayerComponent component
    ) {
        removeOwnedBlindness(player, component);
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        witch.startDeferredCooldownNow();
        witch.sync();
        component.syncOwner();
    }

    private static void forceEnd(
            ServerPlayerEntity player,
            BlackRavenPerceptionPlayerComponent component,
            boolean clearKnowledge
    ) {
        removeOwnedBlindness(player, component);
        if (clearKnowledge) {
            component.clear();
        } else {
            component.cancelActivePreservingKnowledge();
        }
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        witch.clearDeferredCooldownState();
        witch.sync();
    }

    private static void maintainBlindness(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                MAINTAINED_BLINDNESS_TICKS,
                0,
                false,
                false,
                true
        ));
    }

    private static void removeOwnedBlindness(
            ServerPlayerEntity player,
            BlackRavenPerceptionPlayerComponent component
    ) {
        if (component.ownsBlindness()) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
        component.releaseBlindnessOwnership();
    }
}
