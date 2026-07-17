package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owns deferred Wraith promotion and its reconnect-safe queue. */
final class WraithPromotionService {
    private static final Set<UUID> PENDING = new HashSet<>();
    private static boolean registered;

    private WraithPromotionService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(WraithPromotionService::finishPromotions);
    }

    static void resumePlayer(ServerPlayerEntity player) {
        if (WraithPlayerComponent.KEY.get(player).isPromotionPending()) {
            PENDING.add(player.getUuid());
        }
    }

    static void queueIfReady(ServerPlayerEntity player, int completions) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!WraithLifecycleRules.shouldQueuePromotion(
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

    private static void finishPromotions(MinecraftServer server) {
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
        Role role = WraithPromotionRoles.pick(alignment, new java.util.Random(player.getRandom().nextLong()));
        wraith.promote();
        WraithRoleTransitionService.transition(player, role);
        WraithEffectService.removeRestrictedEffects(player);
        WraithVoiceChannelService.restoreLivingChannel(player);
    }
}
