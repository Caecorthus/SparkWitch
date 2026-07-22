package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaLifecycleService;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WraithPromotionQueue {
    private static final int FORCED_RETRY_TICKS = 20;
    private static final Set<UUID> PENDING = new HashSet<>();
    private static final Map<UUID, Integer> RETRY_DELAYS = new HashMap<>();

    private WraithPromotionQueue() {
    }

    static void resumePlayer(ServerPlayerEntity player) {
        if (WraithPlayerComponent.KEY.get(player).isPromotionPending()) {
            PENDING.add(player.getUuid());
        }
    }

    static void queueIfReady(ServerPlayerEntity player, int completions) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!shouldQueuePromotion(
                wraith.isActive(), wraith.isPromoted() || wraith.isPromotionPending(), completions)) {
            return;
        }
        wraith.setPromotionPending(true);
        PENDING.add(player.getUuid());
    }

    static void clearPlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        PENDING.remove(playerUuid);
        RETRY_DELAYS.remove(playerUuid);
    }

    static void clearAll() {
        PENDING.clear();
        RETRY_DELAYS.clear();
    }

    static boolean shouldQueuePromotion(boolean active, boolean alreadyPromotedOrPending, int completions) {
        return active && !alreadyPromotedOrPending && completions >= 3;
    }

    static boolean shouldRetryForcedPromotion(
            boolean forced,
            WraithPromotionService.Failure failure
    ) {
        return forced && failure == WraithPromotionService.Failure.VENDETTA_INELIGIBLE;
    }

    static void finishPromotions(MinecraftServer server) {
        tickRetryDelays();
        for (UUID uuid : new ArrayList<>(PENDING)) {
            PENDING.remove(uuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                promoteIfReady(player);
            }
        }
    }

    private static void tickRetryDelays() {
        for (Map.Entry<UUID, Integer> retry : new ArrayList<>(RETRY_DELAYS.entrySet())) {
            int remaining = retry.getValue() - 1;
            if (remaining <= 0) {
                RETRY_DELAYS.remove(retry.getKey());
                PENDING.add(retry.getKey());
            } else {
                RETRY_DELAYS.put(retry.getKey(), remaining);
            }
        }
    }

    private static void promoteIfReady(ServerPlayerEntity player) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        WraithState.Alignment alignment = wraith.getAlignment();
        if (!wraith.isActive() || !wraith.isPromotionPending()) {
            return;
        }
        if (alignment == null) {
            wraith.setPromotionPending(false);
            return;
        }
        WitchWorldComponent world = WitchWorldComponent.KEY.get(player.getServer().getOverworld());
        net.minecraft.util.Identifier forcedRoleId = world.getForcedWraithPromotion(player.getUuid());
        dev.doctor4t.wathe.api.Role forcedRole = WraithPromotionRoles.find(forcedRoleId);
        if (forcedRoleId != null && forcedRole == null) {
            world.clearForcedWraithPromotion(player.getUuid());
        }
        dev.doctor4t.wathe.api.Role role = forcedRole != null
                ? forcedRole
                : WraithPromotionRoles.pick(
                        alignment,
                        VendettaLifecycleService.canPromote(player),
                        new java.util.Random(player.getRandom().nextLong())
                );
        WraithPromotionService.Result result = forcedRole != null
                ? WraithPromotionService.promoteForced(player, role)
                : WraithPromotionService.promote(player, role);
        if (result.promoted() && forcedRole != null) {
            world.clearForcedWraithPromotion(player.getUuid());
        } else if (shouldRetryForcedPromotion(
                forcedRole != null,
                result.failure()
        )) {
            RETRY_DELAYS.put(player.getUuid(), FORCED_RETRY_TICKS);
        }
    }
}
