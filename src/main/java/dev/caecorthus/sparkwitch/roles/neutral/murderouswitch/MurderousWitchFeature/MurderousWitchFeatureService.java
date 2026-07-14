package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchFeature;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.economy.WitchEconomyService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules.MurderousWitchRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchShop.MurderousWitchShopService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Registers Murderous Witch's explicit neutral-killer bridges.
 * 注册杀意魔女的中立杀手式显式桥接能力。
 */
public final class MurderousWitchFeatureService {
    private static boolean registered;

    private MurderousWitchFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerEffectiveFactionResolver(MurderousWitchFeatureService::effectiveFaction);
        SparkFactionApi.registerEconomyPolicy(MurderousWitchFeatureService::economyDecision);
        SparkFactionApi.registerInstinctPolicy(MurderousWitchFeatureService::instinctHighlight);
        CheckWinCondition.EVENT.register(MurderousWitchFeatureService::checkWin);
        MurderousWitchShopService.register();
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (!MurderousWitchRules.isMurderousWitch(role)) {
            return;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        PlayerShopComponent.KEY.get(player).setBalance(WitchEconomyService.killerStyleStartingMoney(player, gameComponent));
    }

    private static Identifier effectiveFaction(PlayerEntity player, GameWorldComponent gameComponent, Identifier currentFaction) {
        Role role = gameComponent.getRole(player);
        return MurderousWitchRules.isMurderousWitch(role) ? SparkWitchFactions.MURDEROUS_WITCH : null;
    }

    private static Boolean economyDecision(
            PlayerEntity player,
            FactionEconomyPolicy.RewardKind rewardKind,
            GameWorldComponent gameComponent
    ) {
        return MurderousWitchRules.economyDecision(gameComponent.getRole(player), rewardKind);
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        Role viewerRole = gameComponent.getRole(viewer);
        if (!MurderousWitchRules.isMurderousWitch(viewerRole) || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        boolean viewerAlive = GameFunctions.isPlayerPlayingAndAlive(viewer);
        boolean viewerSpectatingOrCreative = GameFunctions.isPlayerSpectatingOrCreative(viewer);
        if (!MurderousWitchRules.shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)) {
            return null;
        }
        Role targetRole = gameComponent.getRole(targetPlayer);
        if (MurderousWitchRules.shouldHardSkipInvisiblePhantom(
                viewerRole,
                targetRole,
                targetPlayer.isInvisible()
        )) {
            return FactionInstinctPolicy.InstinctResult.skip(MurderousWitchRules.HIDDEN_PHANTOM_SKIP_PRIORITY);
        }
        boolean samePlayer = viewer.getUuid().equals(targetPlayer.getUuid());
        boolean shouldHighlight = MurderousWitchRules.shouldHighlightInstinctTarget(
                viewerAlive,
                viewerSpectatingOrCreative,
                samePlayer,
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)
        );
        if (!shouldHighlight) {
            return FactionInstinctPolicy.InstinctResult.skip(MurderousWitchRules.INSTINCT_PRIORITY);
        }
        return FactionInstinctPolicy.InstinctResult.show(
                MurderousWitchRules.INSTINCT_COLOR,
                true,
                MurderousWitchRules.INSTINCT_PRIORITY
        );
    }

    private static CheckWinCondition.WinResult checkWin(
            ServerWorld world,
            GameWorldComponent gameComponent,
            GameFunctions.WinStatus currentStatus
    ) {
        int livingPlayerCount = 0;
        int livingMurderousWitchCount = 0;
        ServerPlayerEntity livingMurderousWitch = null;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            livingPlayerCount++;
            Role role = gameComponent.getRole(player);
            if (MurderousWitchRules.isMurderousWitch(role)) {
                livingMurderousWitchCount++;
                livingMurderousWitch = player;
            }
        }

        return switch (MurderousWitchRules.winAction(livingPlayerCount, livingMurderousWitchCount, currentStatus)) {
            case NEUTRAL_WIN -> CheckWinCondition.WinResult.neutralWin(livingMurderousWitch);
            case BLOCK -> CheckWinCondition.WinResult.block();
            case NONE -> null;
        };
    }
}
