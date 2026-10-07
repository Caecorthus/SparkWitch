package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

/** The provider owns shots and punishment; Emma only observes terminal direct-target kills.
 * 提供方负责射击与误杀惩罚；艾玛仅观察直接命中的最终击杀。 */
public final class EmmaGunService {
    private static final Map<UUID, Shot> SHOTS = new HashMap<>();
    private static boolean registered;

    private EmmaGunService() { }

    public static void register() {
        if (registered) return;
        registered = true;
        SparkTraitsGunBridge.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SHOTS.values().removeIf(shot -> shot.shooter() == handler.player));
    }

    public static void cycleStarted(ServerPlayerEntity shooter, UUID cycleId, Item weapon) {
        if (weapon != WatheItems.REVOLVER || !eligible(shooter)) return;
        SHOTS.put(cycleId, new Shot(shooter, shooter.getServerWorld(), new EmmaGunCycle()));
    }

    public static Object beforeTargetKill(ServerPlayerEntity shooter, UUID cycleId, ServerPlayerEntity target) {
        Shot shot = SHOTS.get(cycleId);
        if (!matches(shot, shooter) || target == shooter || target.getWorld() != shooter.getWorld()) return null;
        return new Attempt(target.getUuid(), GameFunctions.isPlayerPlayingAndAlive(target),
                WitchFactorService.isFactorHolder(target));
    }

    public static void afterTargetKill(ServerPlayerEntity shooter, UUID cycleId,
                                       ServerPlayerEntity target, Object token) {
        Shot shot = SHOTS.get(cycleId);
        if (!matches(shot, shooter) || !(token instanceof Attempt attempt)
                || !attempt.target().equals(target.getUuid()) || !attempt.wasAlive()) return;
        boolean terminal = GameWorldComponent.KEY.get(target.getWorld()).isPlayerDead(target.getUuid())
                && !SparkTraitsGunBridge.deathWasIntercepted(target);
        if (shot.cycle().confirmKill(attempt.wasCarrier(), terminal)) {
            shooter.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, false, false, true));
            applyCooldown(shot);
        }
    }

    public static void initialCooldownEstablished(ServerPlayerEntity shooter, UUID cycleId, Item weapon,
                                                   int startTick, int endTick, int currentTick) {
        Shot shot = SHOTS.get(cycleId);
        if (weapon != WatheItems.REVOLVER || !matches(shot, shooter)) return;
        shot.cycle().establishCooldown(startTick, endTick);
        applyCooldown(shot);
    }

    public static void cycleClosed(ServerPlayerEntity shooter, UUID cycleId) {
        Shot shot = SHOTS.get(cycleId);
        if (shot != null && shot.shooter() == shooter) SHOTS.remove(cycleId);
    }

    private static void applyCooldown(Shot shot) {
        if (!shot.cycle().needsCooldownReward() || !eligible(shot.shooter())) return;
        int clock = EmmaCooldowns.tick(shot.shooter());
        int remaining = shot.cycle().reducedRemaining(clock);
        EmmaCooldowns.setExact(shot.shooter(), WatheItems.REVOLVER, remaining);
        shot.cycle().markCooldownApplied();
    }

    public static boolean eligible(ServerPlayerEntity player) {
        var game = GameWorldComponent.KEY.get(player.getWorld());
        var role = game.getRole(player);
        return game.isRunning() && role != null && EmmaRules.ROLE_ID.equals(role.identifier())
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }

    private static boolean matches(Shot shot, ServerPlayerEntity player) {
        return shot != null && shot.shooter() == player && shot.world() == player.getWorld() && eligible(player);
    }

    private record Shot(ServerPlayerEntity shooter, net.minecraft.server.world.ServerWorld world,
                        EmmaGunCycle cycle) { }
    private record Attempt(UUID target, boolean wasAlive, boolean wasCarrier) { }
}
