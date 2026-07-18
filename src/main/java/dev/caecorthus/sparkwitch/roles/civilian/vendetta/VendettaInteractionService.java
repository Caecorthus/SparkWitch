package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Owns exact-pair queries and the two shared Wathe interaction exceptions. */
public final class VendettaInteractionService {
    private static boolean registered;

    private VendettaInteractionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(VendettaInteractionService::beforeBlackout);
        ShouldPunishGunShooter.EVENT.register(VendettaInteractionService::gunPunishment);
    }

    public static boolean isActiveVendetta(PlayerEntity player) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.maybeGet(player).orElse(null);
        return component != null && component.isActive();
    }

    public static boolean isExactPair(PlayerEntity actor, PlayerEntity target) {
        VendettaPlayerComponent actorComponent = VendettaPlayerComponent.KEY.maybeGet(actor).orElse(null);
        if (actorComponent != null && VendettaRules.isExactPair(
                actor.getUuid(), target.getUuid(), actor.getUuid(), actorComponent.getBoundKillerUuid(),
                actorComponent.isActive())) {
            return true;
        }
        VendettaPlayerComponent targetComponent = VendettaPlayerComponent.KEY.maybeGet(target).orElse(null);
        return targetComponent != null && VendettaRules.isExactPair(
                actor.getUuid(), target.getUuid(), target.getUuid(), targetComponent.getBoundKillerUuid(),
                targetComponent.isActive());
    }

    /** Allows only the killer-to-Vendetta direction when normal target filters reject Wathe-dead players. */
    public static boolean isBoundKillerTargetingVendetta(PlayerEntity actor, PlayerEntity target) {
        if (actor == null || target == null || actor == target) {
            return false;
        }
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.maybeGet(target).orElse(null);
        if (component == null) {
            return false;
        }
        UUID boundKillerUuid = component.getBoundKillerUuid();
        return component.isActive()
                && boundKillerUuid != null
                && boundKillerUuid.equals(actor.getUuid());
    }

    public static boolean isOrdinaryAliveOrBoundKillerTarget(PlayerEntity actor, PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target)
                || isBoundKillerTargetingVendetta(actor, target);
    }

    public static boolean isDamageFromBoundKiller(ServerPlayerEntity victim, DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof PlayerEntity player && isExactPair(player, victim);
    }

    private static @Nullable BlackoutEffect.BlackoutResult beforeBlackout(
            ServerPlayerEntity player,
            int durationTicks
    ) {
        return isActiveVendetta(player) ? BlackoutEffect.BlackoutResult.cancel() : null;
    }

    private static @Nullable ShouldPunishGunShooter.PunishResult gunPunishment(
            PlayerEntity shooter,
            PlayerEntity victim
    ) {
        return isBoundKillerTargetingVendetta(shooter, victim)
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : null;
    }
}
