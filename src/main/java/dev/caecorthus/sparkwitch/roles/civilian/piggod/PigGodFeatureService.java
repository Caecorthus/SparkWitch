package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.caecorthus.sparkwitch.item.rope.RopeShieldPolicy;
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
        // This policy runs on every client several times per visible player per frame, and only a Pig God whose chase
        // is active can ever get a highlight from it. Those two cheap reads go first, so every other viewer skips the
        // liveness lookups and the Survival Master raycast, which is now asked last and only when everything else
        // already allows the highlight. PigGodRules.shouldHighlight is a plain AND of these same inputs, so the result
        // is unchanged.
        // 该策略在每个客户端上对每名可见玩家每帧运行数次，而只有追杀进行中的皮革噶的才可能从中得到高亮。先做这两项廉价读取，
        // 其他观察者因此跳过存活查询与生存大师射线检测；射线检测现在放在最后，且仅在其余条件都已允许高亮时才执行。
        // PigGodRules.shouldHighlight 只是对同一组输入做与运算，因此结果不变。
        Role viewerRole = gameComponent.getRole(viewer);
        if (!PigGodRules.isPigGod(viewerRole)) {
            return null;
        }
        boolean chaseActive = WitchPlayerComponent.KEY.get(viewer).isPigChaseActive();
        if (!chaseActive) {
            return null;
        }
        boolean highlightUnlessHidden = PigGodRules.shouldHighlight(
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                chaseActive,
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                false
        );
        if (!highlightUnlessHidden) {
            return null;
        }
        Role targetRole = gameComponent.getRole(targetPlayer);
        boolean hiddenSurvivalMaster = targetRole != null
                && SURVIVAL_MASTER_ID.equals(targetRole.identifier())
                && !viewer.canSee(targetPlayer);
        if (hiddenSurvivalMaster) {
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
        if (SparkTraitsKillerBridge.isLastEscapeActive(victim)) {
            return null;
        }
        // Rope seam ("不破盾"): the strangle passes the chase freeze. / 绳索接缝（「不破盾」）：勒杀穿过追杀定身的免伤。
        if (RopeShieldPolicy.passesShields(deathReason)) {
            return null;
        }
        return shouldBlockDamage(victim) ? CeremonialSwordProtectionPolicy.afterProtection(deathReason) : null;
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
