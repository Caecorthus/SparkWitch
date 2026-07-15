package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Owns server-authoritative scent promotion, Cologne pulses, and corpse sanity drain.
 * 持有服务端权威的气味晋升、古龙水脉冲与尸体理智下降。
 */
public final class PerfumerRuntime {
    private static final Identifier PERFUMER_ROLE_ID = Identifier.of(PerfumerRules.ROLE_ID);
    private static boolean registered;

    private PerfumerRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(PerfumerRuntime::afterKill);
    }

    /**
     * Called by the owner-only Perfumer component on the logical server.
     * 由仅同步给拥有者的调香师组件在逻辑服务端调用。
     */
    public static void tickPlayer(ServerPlayerEntity player, PerfumerPlayerComponent component) {
        if (component.tickColognePulse() && isActivePlayer(player)) {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
            mood.setMood(PerfumerRules.applyCologneMoodPulse(mood.getMood()));
        }

        if (!isActivePerfumer(player)) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        if (mood.tasks.isEmpty() || !hasNearbyVisibleCorpse(player)) {
            return;
        }
        float baselineDrain = mood.tasks.size() * GameConstants.MOOD_DRAIN;
        float extraDrain = PerfumerRules.extraCorpseMoodDrain(baselineDrain, 1);
        mood.setMood(mood.getMood() - extraDrain);
    }

    static boolean isActivePerfumer(PlayerEntity player) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getWorld());
        return isPerfumerRole(gameComponent.getRole(player))
                && isActivePlayer(player);
    }

    static boolean isActivePlayer(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }

    static boolean isPerfumerRole(@Nullable Role role) {
        return role != null && PERFUMER_ROLE_ID.equals(role.identifier());
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        ServerWorld world = victim.getServerWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        UUID victimUuid = victim.getUuid();
        UUID killerUuid = killer == null ? null : killer.getUuid();
        boolean attributedKill = killerUuid != null && !killerUuid.equals(victimUuid);

        for (ServerPlayerEntity owner : world.getPlayers()) {
            PerfumerPlayerComponent component = PerfumerPlayerComponent.KEY.get(owner);
            if (attributedKill && isPerfumerRole(gameComponent.getRole(owner))) {
                component.promoteMarked(killerUuid);
            }
            component.removeTarget(victimUuid);
        }
    }

    private static boolean hasNearbyVisibleCorpse(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return world.getEntitiesByClass(
                PlayerBodyEntity.class,
                player.getBoundingBox().expand(PerfumerRules.CORPSE_SANITY_RANGE_BLOCKS),
                body -> PerfumerRules.isWithinCorpseSanityRange(player.squaredDistanceTo(body))
                        && !NoellesHiddenBodiesBridge.isHidden(world, body.getPlayerUuid())
        ).stream().findAny().isPresent();
    }
}
