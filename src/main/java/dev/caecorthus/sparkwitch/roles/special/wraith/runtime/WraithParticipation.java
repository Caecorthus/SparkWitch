package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkfactionapi.api.FactionGunPunishmentPolicy;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.block.DrinkTrayBlock;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.CocktailItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;

/**
 * Owns how Wraith players participate in interactions, player-affect policy, dead play, and factions.
 * 负责冤魂玩家的交互、玩家影响策略、死亡参与和阵营解析。
 */
final class WraithParticipation {
    private static final Identifier PROJECTILE_ACTION = Identifier.of("sparkfactionapi", "projectile");
    private static boolean registered;

    private WraithParticipation() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        registerPlayerIsolation();
        registerCollisionExemption();
        registerGunPunishmentProtection();
        registerInteractions();
        SparkFactionApi.registerEffectiveFactionResolver(WraithParticipation::resolveFaction);
    }

    static boolean canAffectPlayer(boolean actorWraith, boolean targetWraith, boolean samePlayer) {
        return samePlayer || (!actorWraith && !targetWraith);
    }

    static @Nullable Identifier restrictedFaction(
            boolean restricted,
            @Nullable WraithState.Alignment alignment
    ) {
        if (!restricted || alignment == null) {
            return null;
        }
        return switch (alignment) {
            case GOOD -> FactionIds.CIVILIAN;
            case KILLER -> FactionIds.KILLER;
            case WITCH -> SparkWitchFactions.WITCH;
        };
    }

    private static void registerCollisionExemption() {
        SparkFactionApi.registerEntityCollisionExemption(entity ->
                entity instanceof PlayerEntity player
                        && WraithStateService.isActive(player));
    }

    private static void registerPlayerIsolation() {
        SparkFactionApi.registerPlayerAffectPolicy((actor, target, actionId, gameComponent) -> {
            if (isWindSpiritProjectile(actionId, actor)) {
                return canWindSpiritProjectileAffect(
                        true,
                        actor.getUuid().equals(target.getUuid()),
                        target.isAlive(),
                        GameFunctions.isPlayerPlayingAndAlive(target),
                        target.isSpectator(),
                        WraithStateService.isActive(target)
                );
            }
            return VendettaInteractionService.isExactPair(actor, target)
                    || canAffectPlayer(
                            WraithStateService.isActive(actor),
                            WraithStateService.isActive(target),
                            actor.getUuid().equals(target.getUuid())
                    );
        });
    }

    static boolean shouldCancelGunPunishment(
            boolean activeWraith,
            boolean promotedWraith,
            @Nullable WraithState.Alignment alignment,
            FactionGunPunishmentPolicy.Subject subject
    ) {
        return subject == FactionGunPunishmentPolicy.Subject.SHOOTER
                && activeWraith
                && promotedWraith
                && alignment == WraithState.Alignment.GOOD;
    }

    private static void registerGunPunishmentProtection() {
        SparkFactionApi.registerGunPunishmentPolicy((player, subject, gameComponent) -> {
            WraithPlayerComponent component = WraithPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (component == null || !shouldCancelGunPunishment(
                    component.isActive(),
                    component.isPromoted(),
                    component.getAlignment(),
                    subject
            )) {
                return null;
            }
            return false;
        });
    }

    static boolean canWindSpiritProjectileAffect(
            boolean activePromotedWindSpiritOwner,
            boolean samePlayer,
            boolean targetAlive,
            boolean targetParticipating,
            boolean targetSpectator,
            boolean targetActiveWraith
    ) {
        return activePromotedWindSpiritOwner
                && !samePlayer
                && targetAlive
                && targetParticipating
                && !targetSpectator
                && !targetActiveWraith;
    }

    private static boolean isWindSpiritProjectile(Identifier actionId, PlayerEntity actor) {
        return PROJECTILE_ACTION.equals(actionId)
                && WindSpiritRules.isActivePromotedWindSpirit(actor);
    }

    private static void registerInteractions() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!WraithStateService.isRestricted(player)) {
                return ActionResult.PASS;
            }
            Object block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            return block instanceof FoodPlatterBlock
                    || block instanceof DrinkTrayBlock
                    || block instanceof BedBlock
                    ? ActionResult.PASS : ActionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                WraithStateService.isRestricted(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            boolean allowed = stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof CocktailItem;
            return WraithStateService.isRestricted(player) && !allowed
                    ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
    }

    private static @Nullable Identifier resolveFaction(
            PlayerEntity player,
            GameWorldComponent gameComponent,
            Identifier currentFaction
    ) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.maybeGet(player).orElse(null);
        return wraith == null ? null : restrictedFaction(wraith.isRestricted(), wraith.getAlignment());
    }
}
