package dev.caecorthus.sparkwitch.item.firepoker;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks short-lived Fire Poker push credit for train-fall deaths.
 * 记录短期烧火棍推人归因，只在坠车死亡结算时补充 killer。
 */
public final class FirePokerFallAttributionService {
    private static final Map<UUID, Attribution> ATTRIBUTIONS = new HashMap<>();

    private FirePokerFallAttributionService() {
    }

    public static void recordPush(ServerPlayerEntity pusher, ServerPlayerEntity target) {
        recordPush(pusher.getUuid(), target.getUuid(), pusher.getServerWorld().getTime());
    }

    static void recordPush(UUID pusherUuid, UUID targetUuid, long currentTime) {
        if (pusherUuid.equals(targetUuid)) {
            return;
        }
        clearExpired(currentTime);
        ATTRIBUTIONS.put(targetUuid, new Attribution(pusherUuid, currentTime + FirePokerRules.FALL_ATTRIBUTION_WINDOW_TICKS));
    }

    public static @Nullable ServerPlayerEntity resolveFallKiller(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity fallbackKiller,
            Identifier deathReason
    ) {
        UUID pusherUuid = consumePusherUuid(victim.getUuid(), deathReason, victim.getServerWorld().getTime());
        if (pusherUuid == null || victim.getServer() == null) {
            return fallbackKiller;
        }

        ServerPlayerEntity pusher = victim.getServer().getPlayerManager().getPlayer(pusherUuid);
        if (pusher == null) {
            return fallbackKiller;
        }
        return pusher;
    }

    static @Nullable UUID consumePusherUuid(UUID targetUuid, Identifier deathReason, long currentTime) {
        if (!GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            return null;
        }

        Attribution attribution = ATTRIBUTIONS.remove(targetUuid);
        if (attribution == null || currentTime > attribution.expiresAtTicks()) {
            clearExpired(currentTime);
            return null;
        }
        return attribution.pusherUuid();
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        clearPlayer(player.getUuid());
    }

    static void clearPlayer(UUID playerUuid) {
        ATTRIBUTIONS.remove(playerUuid);
        ATTRIBUTIONS.entrySet().removeIf(entry -> entry.getValue().pusherUuid().equals(playerUuid));
    }

    public static void clearAll() {
        ATTRIBUTIONS.clear();
    }

    private static void clearExpired(long currentTime) {
        Iterator<Map.Entry<UUID, Attribution>> iterator = ATTRIBUTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Attribution> entry = iterator.next();
            if (currentTime > entry.getValue().expiresAtTicks()) {
                iterator.remove();
            }
        }
    }

    private record Attribution(UUID pusherUuid, long expiresAtTicks) {
    }
}
