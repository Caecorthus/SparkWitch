package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.api.WitchManaApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the one-time transition from a confirmed eligible death into Wraith play.
 * 持有一次已确认合格死亡到冤魂玩法的单次转换。
 */
public final class WraithDeathService {
    private static final Map<UUID, WraithDeathSnapshot> ATTEMPTS = new LinkedHashMap<>();

    private WraithDeathService() {
    }

    /**
     * Called only from the canonical five-argument GameFunctions HEAD mixin.
     * 仅由标准五参数 GameFunctions HEAD mixin 调用。
     */
    public static void captureAttempt(
            ServerPlayerEntity victim,
            Identifier deathReason
    ) {
        UUID uuid = victim.getUuid();
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getWorld());
        Role role = game.getRole(victim);
        Identifier effectiveFaction = role == null ? null : SparkFactionApi.resolveEffectiveFaction(victim, game);
        boolean pushedFall = GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                && victim.getLastAttacker() instanceof ServerPlayerEntity;
        if (WraithStateService.isActive(victim)
                || role == null
                || !WraithRules.isEligibleDeath(effectiveFaction, deathReason, pushedFall)) {
            ATTEMPTS.remove(uuid);
            return;
        }

        try {
            WraithSwallowedCapture swallowed = WraithSwallowedCapture.capture(victim);
            ATTEMPTS.put(uuid, new WraithDeathSnapshot(
                    role.identifier(),
                    effectiveFaction,
                    deathReason,
                    WraithTaskSnapshot.capture(victim),
                    SparkTraitsWraithBridge.capture(victim),
                    WraithRules.effectiveAlignment(effectiveFaction),
                    new WraithReturnPoint(
                            victim.getServerWorld().getRegistryKey(),
                            victim.getPos(),
                            victim.getYaw(),
                            victim.getPitch()
                    ),
                    swallowed.swallowed(),
                    swallowed.taotieLocation(),
                    pushedFall,
                    victim.getServerWorld().getTime()
            ));
        } catch (IllegalArgumentException ignored) {
            ATTEMPTS.remove(uuid);
        }
    }

    /** Called from the canonical kill method TAIL after every provider AFTER listener. */
    public static void finishConfirmedDeath(ServerPlayerEntity victim, Identifier deathReason) {
        WraithDeathSnapshot snapshot = ATTEMPTS.remove(victim.getUuid());
        if (snapshot == null || !snapshot.deathReason().equals(deathReason)) {
            return;
        }
        // Resolve at TAIL after AFTER listeners but before Wathe returns to its world win check.
        // 在 AFTER 监听器完成后、Wathe 返回世界胜负检查前，于 TAIL 内完成。
        activate(victim, snapshot, deathReason);
    }

    private static boolean activate(
            ServerPlayerEntity player,
            WraithDeathSnapshot snapshot,
            Identifier deathReason
    ) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (SparkTraitsWraithBridge.isLastStandDeathIntercepted(player)
                || !game.isRunning()
                || !game.isPlayerDead(player.getUuid())
                || wraith.isActive()
                || !WraithRules.isEligibleDeath(
                        snapshot.originalEffectiveFaction(),
                        deathReason,
                        snapshot.pushedFall()
                )
                || !WraithRoundQuotaService.hasCapacity(player.getServerWorld())) {
            return false;
        }

        WraithDestination destination = WraithPositionResolver.resolve(player, snapshot, deathReason);
        if (destination == null) {
            return false;
        }
        double randomRoll = player.getRandom().nextDouble();
        if (!WraithRules.shouldBecomeWraith(
                snapshot.originalEffectiveFaction(),
                deathReason,
                snapshot.pushedFall(),
                randomRoll
        ) || !WraithRoundQuotaService.tryConsume(player.getServerWorld(), player.getUuid())) {
            return false;
        }

        wraith.activate(snapshot.alignment());
        WraithRoleTransitionService.transition(player, WraithRole.ROLE);
        WraithSessionService.activatePlayer(player);
        player.teleport(
                destination.world(),
                destination.position().getX(),
                destination.position().getY(),
                destination.position().getZ(),
                destination.yaw(),
                destination.pitch()
        );
        player.setCameraEntity(player);
        SparkTraitsWraithBridge.restoreWithCautious(player, snapshot.traits());
        WraithTaskService.restoreForActivation(player, snapshot.tasks());

        // Wathe has already dropped ordinary death items. Clear only what remained afterward.
        // Wathe 已完成普通死亡掉落；这里只清空掉落后仍留在背包中的内容。
        player.closeHandledScreen();
        player.getInventory().clear();
        PlayerShopComponent.KEY.get(player).setBalance(0);
        WitchManaApi.clearMana(player);
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }

    static void clearPlayer(ServerPlayerEntity player) {
        ATTEMPTS.remove(player.getUuid());
    }

    static void clearAll() {
        ATTEMPTS.clear();
    }
}
