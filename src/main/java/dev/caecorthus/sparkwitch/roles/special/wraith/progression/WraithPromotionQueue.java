package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaLifecycleService;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class WraithPromotionQueue {
    private static final Set<UUID> PENDING = new HashSet<>();

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
        PENDING.remove(player.getUuid());
    }

    static void clearAll() {
        PENDING.clear();
    }

    static boolean shouldQueuePromotion(boolean active, boolean alreadyPromotedOrPending, int completions) {
        return active && !alreadyPromotedOrPending && completions >= 3;
    }

    static void finishPromotions(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(PENDING)) {
            PENDING.remove(uuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                promoteIfReady(player);
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
        Role role = WraithPromotionRoles.pick(
                alignment,
                VendettaLifecycleService.canPromote(player),
                new java.util.Random(player.getRandom().nextLong())
        );
        wraith.promote();
        WraithLifecycle.promotePlayer(player, role);
    }
}
