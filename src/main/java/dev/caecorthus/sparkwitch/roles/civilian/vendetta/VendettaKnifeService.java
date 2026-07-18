package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the server-authoritative successful revenge stab transaction. */
public final class VendettaKnifeService {
    private static final long RELEASE_VALID_TICKS = 5L;
    private static final Map<UUID, QualifiedRelease> QUALIFIED_RELEASES = new HashMap<>();

    private VendettaKnifeService() {
    }

    public static void use(ServerPlayerEntity attacker, int targetEntityId) {
        if (attacker == null || attacker.isSpectator()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getServerWorld());
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(attacker);
        Hand heldHand = heldKnifeHand(attacker);
        int heldTicks = consumeQualifiedRelease(attacker);
        if (heldTicks < VendettaKnifeRules.MINIMUM_HOLD_TICKS) {
            return;
        }
        Entity entity = attacker.getServerWorld().getEntityById(targetEntityId);
        if (!(entity instanceof ServerPlayerEntity target)) {
            return;
        }
        UUID boundKillerUuid = component.getBoundKillerUuid();
        boolean activeVendetta = game.isRunning()
                && game.isRole(attacker, SparkWitchRoles.vendetta())
                && WraithStateService.isActive(attacker)
                && component.isActive();
        boolean targetAlive = !target.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && GameFunctions.isPlayerAliveAndSurvival(target);
        if (!VendettaKnifeRules.canAttempt(
                activeVendetta,
                component.isKnifeAvailable(),
                heldHand != null,
                boundKillerUuid != null && boundKillerUuid.equals(target.getUuid()),
                targetAlive,
                heldTicks,
                attacker.squaredDistanceTo(target),
                attacker.canSee(target))) {
            return;
        }

        boolean deadBefore = game.isPlayerDead(target.getUuid());
        GameFunctions.killPlayer(target, true, attacker, GameConstants.DeathReasons.KNIFE);
        boolean deadAfter = game.isPlayerDead(target.getUuid());
        if (!VendettaKnifeRules.confirmedDeath(deadBefore, deadAfter)) {
            return;
        }

        GameRecordManager.recordItemUse(
                attacker,
                Registries.ITEM.getId(SparkWitchItems.vendettaKnife()),
                target,
                null
        );
        target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        attacker.swingHand(heldHand);
    }

    /** Records the real server-side release so a custom payload cannot skip the half-second charge. */
    public static void recordServerRelease(ServerPlayerEntity player, int heldTicks) {
        if (player == null || heldTicks < VendettaKnifeRules.MINIMUM_HOLD_TICKS) {
            if (player != null) {
                QUALIFIED_RELEASES.remove(player.getUuid());
            }
            return;
        }
        QUALIFIED_RELEASES.put(player.getUuid(), new QualifiedRelease(
                heldTicks,
                player.getServerWorld().getTime() + RELEASE_VALID_TICKS
        ));
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        if (player != null) {
            QUALIFIED_RELEASES.remove(player.getUuid());
        }
    }

    public static void clearAll() {
        QUALIFIED_RELEASES.clear();
    }

    private static Hand heldKnifeHand(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(SparkWitchItems.vendettaKnife())) {
            return Hand.MAIN_HAND;
        }
        if (player.getOffHandStack().isOf(SparkWitchItems.vendettaKnife())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private static int consumeQualifiedRelease(ServerPlayerEntity player) {
        QualifiedRelease release = QUALIFIED_RELEASES.remove(player.getUuid());
        return release != null && player.getServerWorld().getTime() <= release.expiresAt()
                ? release.heldTicks() : -1;
    }

    private record QualifiedRelease(int heldTicks, long expiresAt) {
    }
}
