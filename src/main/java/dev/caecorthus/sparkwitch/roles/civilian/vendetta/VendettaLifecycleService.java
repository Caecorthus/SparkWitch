package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Owns Vendetta promotion, reconnect, reveal ticking, and player-local cleanup. */
public final class VendettaLifecycleService {
    private VendettaLifecycleService() {
    }

    public static void captureCreditedKiller(ServerPlayerEntity player, @Nullable UUID killerUuid) {
        VendettaPlayerComponent.KEY.get(player).stageCreditedKiller(killerUuid);
    }

    public static boolean canPromote(ServerPlayerEntity player) {
        UUID killerUuid = VendettaPlayerComponent.KEY.get(player).getBoundKillerUuid();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return VendettaRules.canPromote(
                killerUuid != null,
                killerUuid != null && game.hasAnyRole(killerUuid),
                killerUuid != null && game.isPlayerDead(killerUuid),
                player.getUuid().equals(killerUuid)
        );
    }

    public static void initializeForPromotion(ServerPlayerEntity player, Role role) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
        if (!isVendettaRole(role)) {
            VendettaDisconnectService.forgetBinding(player);
            component.clear();
            return;
        }
        if (!canPromote(player) || !component.activateForPromotion()) {
            WraithLifecycle.terminatePromotedPlayer(player);
            return;
        }
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        player.setInvulnerable(false);
        VendettaDisconnectService.rememberBinding(player, component);
        VendettaKnifeLoadoutService.initializeForPromotion(player);
    }

    /** Restores a valid bond without resetting either reveal timer. / 恢复有效绑定，但不重置任一透视计时。 */
    public static boolean resumePlayer(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
        Role role = game.getRole(player);
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!isVendettaRole(role)) {
            // Restricted Wraith keeps the dormant first-killer candidate across reconnects.
            // 受限冤魂重连时继续保留尚未激活的首次凶手候选绑定。
            if (!wraith.isRestricted()) {
                VendettaDisconnectService.forgetBinding(player);
                component.clear();
            }
            return true;
        }
        if (!wraith.isActive() || !wraith.isPromoted() || !component.isActive()
                || !hasViableBoundKiller(player, component, game)) {
            WraithLifecycle.terminatePromotedPlayer(player);
            return false;
        }
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        player.setInvulnerable(false);
        VendettaDisconnectService.rememberBinding(player, component);
        tickOwner(player, component);
        return component.isActive();
    }

    public static void tickOwner(ServerPlayerEntity owner, VendettaPlayerComponent component) {
        if (!component.isActive()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getServerWorld());
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(owner);
        if (!wraith.isActive() || !wraith.isPromoted() || !isVendettaRole(game.getRole(owner))) {
            VendettaDisconnectService.forgetBinding(owner);
            component.clear();
            return;
        }
        if (!hasViableBoundKiller(owner, component, game)) {
            WraithLifecycle.terminatePromotedPlayer(owner);
            return;
        }
        VendettaDisconnectService.rememberBinding(owner, component);
        UUID killerUuid = component.getBoundKillerUuid();
        boolean wasPaused = component.isTimerPaused();
        int oldCooldown = component.getRevealCooldownTicks();
        int oldReveal = component.getRevealActiveTicks();
        boolean bothOnline = killerUuid != null
                && owner.getServer().getPlayerManager().getPlayer(killerUuid) != null;
        component.tickTimer(bothOnline);
        boolean phaseChanged = oldReveal == 0 && component.getRevealActiveTicks() > 0
                || oldReveal > 0 && component.getRevealActiveTicks() == 0;
        boolean secondChanged = oldCooldown / 20 != component.getRevealCooldownTicks() / 20
                || oldReveal / 20 != component.getRevealActiveTicks() / 20;
        if (wasPaused != component.isTimerPaused() || phaseChanged || secondChanged) {
            component.sync();
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        VendettaDisconnectService.forgetBinding(player);
        VendettaKnifeService.clearPlayer(player);
        VendettaKnifeLoadoutService.removeAll(player);
        VendettaPlayerComponent.KEY.get(player).clear();
    }

    private static boolean hasViableBoundKiller(
            ServerPlayerEntity owner,
            VendettaPlayerComponent component,
            GameWorldComponent game
    ) {
        UUID killerUuid = component.getBoundKillerUuid();
        return killerUuid != null
                && !owner.getUuid().equals(killerUuid)
                && game.hasAnyRole(killerUuid)
                && !game.isPlayerDead(killerUuid);
    }

    private static boolean isVendettaRole(@Nullable Role role) {
        return role != null && SparkWitchRoles.VENDETTA_ID.equals(role.identifier());
    }
}
