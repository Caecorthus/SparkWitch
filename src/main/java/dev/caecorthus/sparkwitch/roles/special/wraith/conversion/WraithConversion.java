package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.component.WraithRoundComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithProgression;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaLifecycleService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the complete capture, confirmation, deferred activation, corpse, and round-quota conversion flow.
 * 负责完整的捕获、确认、延迟激活、尸体与本局名额转化流程。
 */
public final class WraithConversion {
    private static final Map<UUID, WraithDeathSnapshot> CAPTURED = new HashMap<>();
    private static final Map<UUID, PendingDeath> PENDING = new HashMap<>();
    private static boolean registered;

    private WraithConversion() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WraithSwapperFallAttribution.register();
        ServerTickEvents.END_SERVER_TICK.register(WraithConversion::finishActivations);
    }

    /**
     * Captures the pre-mutation snapshot at the GameFunctions HEAD mixin seam.
     * 在 GameFunctions HEAD Mixin seam 捕获修改前快照。
     */
    public static void captureBeforeMutation(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        UUID uuid = victim.getUuid();
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(victim);
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getWorld());
        Role role = game.getRole(victim);
        if (wraith.isActive() || role == null) {
            CAPTURED.remove(uuid);
            return;
        }
        UUID creditedKillerUuid = resolveCreditedKiller(victim, killer, deathReason);
        if (!WraithRules.isEligibleDeath(
                role.getFaction(), deathReason, creditedKillerUuid != null)) {
            CAPTURED.remove(uuid);
            return;
        }

        try {
            CAPTURED.put(uuid, new WraithDeathSnapshot(
                    role.identifier(),
                    role.getFaction(),
                    WraithProgression.capture(victim),
                    SparkTraitsWraithBridge.capture(victim),
                    WraithRules.alignmentFor(SparkFactionApi.resolveEffectiveFaction(victim, game)),
                    SparkTraitsWraithBridge.hasLastStandTriggered(victim.getServerWorld(), uuid),
                    creditedKillerUuid,
                    (int) victim.getServerWorld().getTime()
            ));
        } catch (IllegalArgumentException ignored) {
            CAPTURED.remove(uuid);
        }
    }

    /** Queues only deaths that reached Wathe's confirmed AFTER seam. */
    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (dev.doctor4t.wathe.game.GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            // The provider entry survives canceled BEFORE attempts and is consumed only at confirmed AFTER.
            // 归因会跨过被取消的 BEFORE 尝试，只在确认到达 AFTER 后消费。
            WraithSwapperFallAttribution.consumeResponsibleUuid(victim);
        }
        WraithDeathSnapshot snapshot = CAPTURED.remove(victim.getUuid());
        if (snapshot != null) {
            PENDING.put(victim.getUuid(), new PendingDeath(deathReason, snapshot));
        }
    }

    public static void beginRound(ServerWorld world, int startingPlayerCount) {
        WraithRoundComponent.KEY.get(world).beginRound(startingPlayerCount);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        CAPTURED.remove(uuid);
        PENDING.remove(uuid);
    }

    public static void clearRound(ServerWorld world) {
        CAPTURED.clear();
        PENDING.clear();
        WraithSwapperFallAttribution.clear();
        WraithRoundComponent.KEY.get(world).clearRound();
    }

    private static void finishActivations(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(PENDING.keySet())) {
            PendingDeath pending = PENDING.remove(uuid);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(uuid);
            if (pending != null && victim != null) {
                activate(victim, pending.deathReason(), pending.snapshot());
            }
        }
    }

    private static boolean activate(
            ServerPlayerEntity victim,
            Identifier deathReason,
            WraithDeathSnapshot snapshot
    ) {
        if (SparkTraitsWraithBridge.didLastStandTriggerSince(victim, snapshot.lastStandTriggeredBefore())) {
            return false;
        }
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(victim);
        WraithRoundComponent round = WraithRoundComponent.KEY.get(victim.getServerWorld());
        if (wraith.isActive()
                || !WraithRules.isEligibleDeath(
                        snapshot.originalFaction(), deathReason, snapshot.creditedKillerUuid() != null)
                || !round.hasCapacity()
                || !WraithRules.passesChance(victim.getRandom().nextDouble())
                || !round.tryConsume(victim.getUuid())) {
            return false;
        }

        ensureDeathBody(victim, deathReason, snapshot.deathGameTime(), snapshot.originalRoleId());
        SparkTraitsWraithBridge.restore(victim, snapshot.traitSnapshot());
        VendettaLifecycleService.captureCreditedKiller(victim, snapshot.creditedKillerUuid());
        wraith.activate(snapshot.alignment());
        WraithLifecycle.activateConvertedPlayer(victim, snapshot.taskSnapshot());
        victim.closeHandledScreen();
        victim.getInventory().clear();
        victim.currentScreenHandler.sendContentUpdates();
        return true;
    }

    private static void ensureDeathBody(
            ServerPlayerEntity player,
            Identifier deathReason,
            int deathGameTime,
            Identifier originalRoleId
    ) {
        ServerWorld world = player.getServerWorld();
        PlayerBodyEntity existingBody = world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> isDeathBody(body, player.getUuid(), deathGameTime)
        ).stream().findFirst().orElse(null);
        if (existingBody != null) {
            ((WraithBodyRoleAccess) existingBody).sparkwitch$setDeathRole(originalRoleId);
            return;
        }

        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return;
        }
        body.setPlayerUuid(player.getUuid());
        // setPlayerUuid snapshots the live role, which may have changed during deferred listeners.
        // setPlayerUuid 会快照当前身份，但延迟监听期间该身份可能已经改变。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(originalRoleId);
        body.setDeathReason(deathReason);
        body.setDeathGameTime(deathGameTime);
        Vec3d spawnPos = player.getPos().add(player.getRotationVector().normalize());
        body.refreshPositionAndAngles(spawnPos.getX(), player.getY(), spawnPos.getZ(), player.getHeadYaw(), 0.0F);
        body.setYaw(player.getHeadYaw());
        body.setHeadYaw(player.getHeadYaw());
        world.spawnEntity(body);
    }

    private static boolean isDeathBody(PlayerBodyEntity body, UUID playerUuid, int deathGameTime) {
        return playerUuid.equals(body.getPlayerUuid()) && body.getDeathGameTime() == deathGameTime;
    }

    private static @Nullable UUID resolveCreditedKiller(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        UUID victimUuid = victim.getUuid();
        if (killer != null && !victimUuid.equals(killer.getUuid())) {
            return killer.getUuid();
        }
        if (!dev.doctor4t.wathe.game.GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            return null;
        }
        UUID responsibleUuid = WraithSwapperFallAttribution.peekResponsibleUuid(victim);
        return victimUuid.equals(responsibleUuid) ? null : responsibleUuid;
    }

    private record PendingDeath(Identifier deathReason, WraithDeathSnapshot snapshot) {
    }
}
