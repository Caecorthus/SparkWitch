package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class EmmaLifecycle {
    private static boolean registered;
    private static final Map<UUID, StatusEffectInstance> OWN_SPEED = new HashMap<>();

    private EmmaLifecycle() { }

    public static void register() {
        if (registered) return;
        registered = true;
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            EmmaRoundComponent round = EmmaRoundComponent.KEY.get(player.getWorld());
            EmmaPlayerComponent personal = EmmaPlayerComponent.KEY.get(player);
            if (EmmaRules.isEmma(role)) {
                personal.joinRound(round.roundId());
                if (round.grantLoadout(player.getUuid())) {
                    serverPlayer.getInventory().insertStack(WatheItems.REVOLVER.getDefaultStack());
                }
            } else {
                personal.clear();
                removeOwnedSpeed(serverPlayer);
            }
        });
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim) && !EmmaTerminalService.isBacklash(reason)) return;
            EmmaRoundComponent.KEY.get(victim.getWorld()).onTerminalDeath(victim.getUuid());
            EmmaPlayerComponent.KEY.get(victim).clear();
            removeOwnedSpeed(victim);
        });
        ResetPlayer.EVENT.register(player -> {
            EmmaPlayerComponent.KEY.get(player).clear();
            if (player instanceof ServerPlayerEntity serverPlayer) removeOwnedSpeed(serverPlayer);
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                EmmaRoundComponent.KEY.get(world).clear();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    EmmaPlayerComponent.KEY.get(player).clear();
                    removeOwnedSpeed(player);
                }
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(EmmaLifecycle::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> OWN_SPEED.clear());
    }

    public static void beginRound(ServerWorld world) {
        EmmaRoundComponent round = EmmaRoundComponent.KEY.get(world);
        round.begin();
        for (ServerPlayerEntity player : world.getPlayers()) {
            EmmaPlayerComponent.KEY.get(player).joinRound(round.roundId());
            removeOwnedSpeed(player);
        }
    }

    private static void tick(ServerWorld world) {
        EmmaRoundComponent round = EmmaRoundComponent.KEY.get(world);
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!round.active() || !game.isRunning()) return;
        for (var entry : Map.copyOf(round.penalties()).entrySet()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.getServerWorld() != world) continue;
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                if (!WitchFactorTraitsBridge.isDeathIntercepted(player)) round.penalties().remove(entry.getKey());
                continue;
            }
            EmmaRoundComponent.Penalty pending = entry.getValue();
            if (pending.deathAt() >= 0 && pending.deathAt() <= world.getTime()) {
                round.penalties().remove(entry.getKey());
                player.sendMessage(Text.translatable("hud.voodoo.cursed"), true);
                EmmaTerminalService.killFromBacklash(player);
            } else if (pending.moodAt() >= 0 && pending.moodAt() <= world.getTime()) {
                if (pending.deathAt() < 0) round.penalties().remove(entry.getKey());
                else round.penalties().put(entry.getKey(), new EmmaRoundComponent.Penalty(pending.deathAt(), -1));
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
                mood.setMood(mood.getMood() - 0.5f);
            }
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            EmmaPlayerComponent personal = EmmaPlayerComponent.KEY.get(player);
            personal.joinRound(round.roundId());
            if (!EmmaRules.isEmma(game.getRole(player)) || !GameFunctions.isPlayerPlayingAndAlive(player)
                    || round.died(player.getUuid())) {
                removeOwnedSpeed(player);
                continue;
            }
            if (WitchFactorService.hasReachedSpeedThreshold(world)) personal.unlockSpeed();
            if (personal.speedUnlocked()) {
                StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
                if (current == null || (current.getAmplifier() == 0 && current.getDuration() <= 20)) {
                    StatusEffectInstance speed = new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, true);
                    player.addStatusEffect(speed);
                    OWN_SPEED.put(player.getUuid(), player.getStatusEffect(StatusEffects.SPEED));
                }
            }
        }
    }

    private static void removeOwnedSpeed(ServerPlayerEntity player) {
        StatusEffectInstance owned = OWN_SPEED.remove(player.getUuid());
        if (owned != null && owned == player.getStatusEffect(StatusEffects.SPEED)
                && owned.getAmplifier() == 0 && owned.getDuration() <= 40) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }
}
