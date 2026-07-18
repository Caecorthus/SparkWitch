package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/** Owns both terminal outcomes after an exact Vendetta bond has been established. */
public final class VendettaTerminalService {
    private static boolean registered;

    private VendettaTerminalService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(VendettaTerminalService::afterKill);
    }

    /** Resolves the killer's second kill before Wathe rejects an already-dead participant. */
    public static boolean tryResolveBoundKillerVictory(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null
                || !VendettaInteractionService.isActiveVendetta(victim)
                || !VendettaInteractionService.isExactPair(killer, victim)) {
            return false;
        }
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(victim);
        UUID boundKillerUuid = component.getBoundKillerUuid();
        if (boundKillerUuid == null || !boundKillerUuid.equals(killer.getUuid())) {
            return false;
        }

        // Clear first so repeated kill entry cannot duplicate rewards or terminal effects.
        // 先清除绑定，确保重复进入死亡入口不会重复发放奖励或终止效果。
        component.clear();
        VendettaKnifeService.clearPlayer(victim);
        VendettaKnifeLoadoutService.removeAll(victim);
        VendettaReplayService.recordTerminal(victim, killer, deathReason);
        PlayerShopComponent.KEY.maybeGet(killer).ifPresent(shop ->
                shop.addToBalance(VendettaRules.TERMINAL_MONEY_REWARD));
        WitchPlayerComponent.KEY.maybeGet(killer).ifPresent(mana -> {
            if (mana.hasManaSystem()) {
                mana.addMana(VendettaRules.TERMINAL_MANA_REWARD);
            }
        });
        spawnVultureParticles(victim);
        WraithLifecycle.terminatePromotedPlayer(victim);
        return true;
    }

    /** Completes revenge after the bound killer has reached Wathe's ordinary confirmed death. */
    public static void completeSuccessfulRevenge(ServerPlayerEntity vendetta) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(vendetta);
        if (!component.isActive()) {
            return;
        }
        component.consumeKnifeAfterConfirmedDeath();
        WraithLifecycle.terminatePromotedPlayer(vendetta);
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        UUID deadUuid = victim.getUuid();
        for (ServerPlayerEntity player : new ArrayList<>(
                victim.getServer().getPlayerManager().getPlayerList())) {
            VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
            if (!component.isActive() || !deadUuid.equals(component.getBoundKillerUuid())) {
                continue;
            }
            if (killer != null && killer.getUuid().equals(player.getUuid())) {
                completeSuccessfulRevenge(player);
            } else {
                WraithLifecycle.terminatePromotedPlayer(player);
            }
        }
        VendettaDisconnectService.onBoundKillerRemoved(deadUuid);
    }

    private static void spawnVultureParticles(ServerPlayerEntity victim) {
        double x = victim.getX();
        double y = victim.getY() + 0.5D;
        double z = victim.getZ();
        victim.getServerWorld().spawnParticles(
                ParticleTypes.SMOKE, x, y, z, 30, 0.3D, 0.3D, 0.3D, 0.02D);
        victim.getServerWorld().spawnParticles(
                ParticleTypes.SOUL, x, y, z, 10, 0.2D, 0.2D, 0.2D, 0.01D);
    }
}
