package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.mixin.PlayerManagerVendettaAccessor;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Owns active Vendetta bindings while either side is disconnected. */
public final class VendettaDisconnectService {
    private static final Map<UUID, UUID> ACTIVE_BINDINGS = new HashMap<>();
    private static final Map<UUID, PendingBoundKillerEscape> PENDING_ESCAPES = new HashMap<>();
    private static boolean registered;

    private VendettaDisconnectService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_ESCAPES.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(VendettaDisconnectService::tickPendingEscapes);
        ServerLifecycleEvents.SERVER_STOPPING.register(VendettaDisconnectService::flushPendingEscapes);
    }

    public static void rememberBinding(
            ServerPlayerEntity owner,
            VendettaPlayerComponent component
    ) {
        UUID killerUuid = component.getBoundKillerUuid();
        if (component.isActive() && killerUuid != null) {
            ACTIVE_BINDINGS.put(owner.getUuid(), killerUuid);
        }
    }

    public static void forgetBinding(ServerPlayerEntity owner) {
        ACTIVE_BINDINGS.remove(owner.getUuid());
    }

    public static void onBoundKillerRemoved(UUID killerUuid) {
        ACTIVE_BINDINGS.entrySet().removeIf(entry -> killerUuid.equals(entry.getValue()));
        PENDING_ESCAPES.remove(killerUuid);
    }

    /** Defers Wathe's disconnect escape until the bounded reconnect grace expires. */
    public static boolean shouldPauseOfflineBoundKillerEscape(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force
    ) {
        if (killer != null
                || !force
                || !GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                || victim.getServer() == null
                || victim.networkHandler.isConnectionOpen()) {
            return false;
        }
        boolean tracked = ACTIVE_BINDINGS.containsValue(victim.getUuid())
                || PENDING_ESCAPES.containsKey(victim.getUuid());
        if (!tracked) {
            return false;
        }
        ServerPlayerEntity onlinePlayer = victim.getServer().getPlayerManager().getPlayer(victim.getUuid());
        if (onlinePlayer != null && onlinePlayer != victim) {
            // The reconnected entity already owns this UUID. / 同 UUID 已由重连实体接管。
            return true;
        }
        PENDING_ESCAPES.putIfAbsent(
                victim.getUuid(),
                new PendingBoundKillerEscape(victim, victim.getServer().getTicks())
        );
        return true;
    }

    /** Clears UUID-only state that online-player cleanup cannot reach. */
    public static void clearRoundState() {
        for (PendingBoundKillerEscape pending : new ArrayList<>(PENDING_ESCAPES.values())) {
            ServerPlayerEntity player = pending.player();
            if (player.getServer().getPlayerManager().getPlayer(player.getUuid()) == null) {
                GameFunctions.resetPlayer(player);
                persistDisconnectedPlayer(player);
            }
        }
        clearRuntimeCaches();
    }

    private static void tickPendingEscapes(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingBoundKillerEscape>> iterator =
                PENDING_ESCAPES.entrySet().iterator();
        ArrayList<PendingBoundKillerEscape> expired = new ArrayList<>();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingBoundKillerEscape> entry = iterator.next();
            if (server.getPlayerManager().getPlayer(entry.getKey()) != null) {
                iterator.remove();
                continue;
            }
            PendingBoundKillerEscape pending = entry.getValue();
            if (!VendettaRules.hasReconnectGraceExpired(
                    pending.disconnectedAtTick(), server.getTicks())) {
                continue;
            }
            iterator.remove();
            expired.add(pending);
        }

        for (PendingBoundKillerEscape pending : expired) {
            confirmEscapeOrReset(pending.player());
        }
    }

    private static void flushPendingEscapes(MinecraftServer server) {
        for (PendingBoundKillerEscape pending : new ArrayList<>(PENDING_ESCAPES.values())) {
            confirmEscapeOrReset(pending.player());
        }
        clearRuntimeCaches();
    }

    private static void confirmEscapeOrReset(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        onBoundKillerRemoved(player.getUuid());
        if (game.isRunning()
                && game.hasAnyRole(player.getUuid())
                && !game.isPlayerDead(player.getUuid())) {
            // Remove the binding first so the confirming call cannot defer itself again.
            // 先移除绑定，避免确认调用再次延后自身。
            GameFunctions.killPlayer(
                    player, true, null, GameConstants.DeathReasons.ESCAPED, true);
        } else if (!game.isPlayerDead(player.getUuid())) {
            GameFunctions.resetPlayer(player);
        }
        persistDisconnectedPlayer(player);
    }

    private static void clearRuntimeCaches() {
        ACTIVE_BINDINGS.clear();
        PENDING_ESCAPES.clear();
    }

    /** Persists mutations made after the live connection entity has gone away. / 持久化离线实体上的结算变更。 */
    private static void persistDisconnectedPlayer(ServerPlayerEntity player) {
        ((PlayerManagerVendettaAccessor) player.getServer().getPlayerManager())
                .sparkwitch$savePlayerData(player);
    }

    private record PendingBoundKillerEscape(ServerPlayerEntity player, long disconnectedAtTick) {
    }
}
