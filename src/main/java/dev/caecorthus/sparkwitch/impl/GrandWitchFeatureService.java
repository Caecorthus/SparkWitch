package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.OptionalInt;

/**
 * Registers explicit Witch faction feature bridges without using Wathe's native killer bucket.
 * 注册魔女阵营的显式能力桥接，不使用 wathe 原生杀手阵营桶。
 */
public final class GrandWitchFeatureService {
    private static final int GRAND_WITCH_INSTINCT_PRIORITY = 200;
    private static final int OBSCURE_SKIP_PRIORITY = 1_000;
    private static boolean registered;

    private GrandWitchFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerEconomyPolicy(GrandWitchFeatureService::economyDecision);
        SparkFactionApi.registerInstinctPolicy(GrandWitchFeatureService::instinctHighlight);
        BlackoutEffect.BEFORE.register(GrandWitchFeatureService::beforeBlackoutEffect);
        GrandWitchShopService.register();
        AccompliceShopService.register();
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (GrandWitchRules.isGrandWitch(role)) {
            PlayerShopComponent.KEY.get(player).setBalance(GrandWitchRules.STARTING_MONEY);
            player.giveItemStack(new ItemStack(WatheItems.KNIFE));
        } else if (GrandWitchRules.isAccomplice(role)) {
            GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
            PlayerShopComponent.KEY.get(player).setBalance(WitchEconomyService.accompliceStartingMoney(player, gameComponent));
            GrandWitchActiveSkillService.clearCeremonialSword(player, false);
        } else {
            GrandWitchActiveSkillService.clearCeremonialSword(player, false);
        }
    }

    public static void clearPlayerRuntime(ServerPlayerEntity player) {
        GrandWitchActiveSkillService.clearCeremonialSword(player, false);
    }

    private static Boolean economyDecision(
            PlayerEntity player,
            dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy.RewardKind rewardKind,
            GameWorldComponent gameComponent
    ) {
        return GrandWitchRules.economyDecision(gameComponent.getRole(player), rewardKind);
    }

    private static BlackoutEffect.BlackoutResult beforeBlackoutEffect(ServerPlayerEntity player, int durationTicks) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return GrandWitchRules.isWitchFactionMember(role) ? BlackoutEffect.BlackoutResult.cancel() : null;
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        Role viewerRole = gameComponent.getRole(viewer);
        if (WitchWorldComponent.KEY.get(viewer.getWorld()).isInstinctObscured()
                && GrandWitchRules.isAffectedByWitchAreaSpell(viewerRole)) {
            return FactionInstinctPolicy.InstinctResult.skip(OBSCURE_SKIP_PRIORITY);
        }

        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        Role targetRole = gameComponent.getRole(targetPlayer);
        OptionalInt color = GrandWitchRules.instinctColor(viewerRole, targetRole);
        if (color.isEmpty()) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
            return FactionInstinctPolicy.InstinctResult.skip(GRAND_WITCH_INSTINCT_PRIORITY);
        }
        return FactionInstinctPolicy.InstinctResult.show(color.getAsInt(), true, GRAND_WITCH_INSTINCT_PRIORITY);
    }
}
