package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges Pig God's temporary chase powers through public Wathe/Fabric/SparkFactionAPI hooks.
 * 通过公开的 Wathe、Fabric 与 SparkFactionAPI 挂钩桥接皮革噶的的临时追杀能力。
 */
public final class PigGodFeatureService {
    private static final Identifier SURVIVAL_MASTER_ID = Identifier.of("noellesroles", "survival_master");
    private static boolean registered;

    private PigGodFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerInstinctPolicy(PigGodFeatureService::instinctHighlight);
        DoorInteraction.EVENT.register(PigGodFeatureService::doorInteraction);
        KillPlayer.BEFORE.register(PigGodFeatureService::beforeKill);
        KillPlayer.AFTER.register(PigGodFeatureService::afterKill);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PigGodFeatureService::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(PigGodFeatureService::allowDeath);
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        Role viewerRole = gameComponent.getRole(viewer);
        Role targetRole = gameComponent.getRole(targetPlayer);
        boolean hiddenSurvivalMaster = targetRole != null
                && SURVIVAL_MASTER_ID.equals(targetRole.identifier())
                && !viewer.canSee(targetPlayer);
        boolean shouldHighlight = PigGodRules.shouldHighlight(
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                WitchPlayerComponent.KEY.get(viewer).isPigChaseActive(),
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                hiddenSurvivalMaster
        );
        if (!shouldHighlight) {
            return null;
        }
        return FactionInstinctPolicy.InstinctResult.show(
                PigGodRules.COLOR,
                false,
                PigGodRules.INSTINCT_PRIORITY
        );
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        Role role = GameWorldComponent.KEY.get(context.getWorld()).getRole(player);
        boolean shouldBlast = PigGodRules.shouldUseDoorBlast(
                role,
                WitchPlayerComponent.KEY.get(player).isPigChaseActive(),
                context.isServerSide(),
                context.isBlasted(),
                context.getDoorType()
        );
        if (!shouldBlast) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        context.getWorld().playSound(
                null,
                context.getPos(),
                WatheSounds.ITEM_CROWBAR_PRY,
                SoundCategory.BLOCKS,
                PigGodRules.DOOR_BLAST_SOUND_VOLUME,
                PigGodRules.DOOR_BLAST_SOUND_PITCH
        );
        context.getEntity().blast();
        return DoorInteraction.DoorInteractionResult.HANDLED;
    }

    private static KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        return shouldBlockDamage(victim) ? KillPlayer.KillResult.cancel() : null;
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null || killer.getUuid().equals(victim.getUuid())) {
            return;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        boolean victimCivilian = FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(
                victim,
                gameComponent
        ));
        boolean shouldPunish = PigGodRules.shouldPunishPigChaseCivilianKill(
                gameComponent.getRole(killer),
                WitchPlayerComponent.KEY.get(killer).isPigChaseActive(),
                GameFunctions.isPlayerPlayingAndAlive(killer),
                victimCivilian
        );
        if (!shouldPunish) {
            return;
        }

        // Mirrors Wathe's innocent-shot penalty for Pig God's chase-mode civilian kills.
        // 追杀期杀死好人时，沿用 Wathe 的误杀好人惩罚让皮革噶的立刻暴毙。
        GameFunctions.killPlayer(killer, true, null, GameConstants.DeathReasons.SHOT_INNOCENT, true);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        return !(entity instanceof ServerPlayerEntity player) || !shouldBlockDamage(player);
    }

    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        return !(entity instanceof ServerPlayerEntity player) || !shouldBlockDamage(player);
    }

    private static boolean shouldBlockDamage(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return PigGodRules.shouldBlockDamage(role, WitchPlayerComponent.KEY.get(player).isPigChaseFreezeActive());
    }
}
