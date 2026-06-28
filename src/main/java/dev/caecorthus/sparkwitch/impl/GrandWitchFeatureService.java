package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.FirecrackerEntity;
import dev.doctor4t.wathe.entity.NoteEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.OptionalInt;

/**
 * Registers explicit Witch faction feature bridges without using Wathe's native killer bucket.
 * 注册魔女阵营的显式能力桥接，不使用 wathe 原生杀手阵营桶。
 */
public final class GrandWitchFeatureService {
    private static final int GRAND_WITCH_INSTINCT_PRIORITY = GrandWitchRules.INSTINCT_PRIORITY;
    private static final int APPRENTICE_OUTLINE_PRIORITY = 300;
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
        boolean viewerAlive = GameFunctions.isPlayerPlayingAndAlive(viewer);
        boolean viewerSpectatingOrCreative = GameFunctions.isPlayerSpectatingOrCreative(viewer);
        if (!GrandWitchRules.shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)) {
            return null;
        }
        if (GrandWitchRules.shouldObscureInstinct(
                WitchWorldComponent.KEY.get(viewer.getWorld()).isInstinctObscured(),
                viewerRole,
                viewerAlive,
                viewerSpectatingOrCreative
        )) {
            return FactionInstinctPolicy.InstinctResult.skip(OBSCURE_SKIP_PRIORITY);
        }

        if (isDefaultDroppedInstinctTarget(target)) {
            OptionalInt droppedItemColor = GrandWitchRules.droppedItemInstinctColor(viewerRole);
            if (droppedItemColor.isPresent()) {
                return FactionInstinctPolicy.InstinctResult.show(
                        droppedItemColor.getAsInt(),
                        true,
                        GRAND_WITCH_INSTINCT_PRIORITY
                );
            }
        }

        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }

        FactionInstinctPolicy.InstinctResult apprenticeOutline = apprenticeOutline(viewer, targetPlayer);
        if (apprenticeOutline != null) {
            return apprenticeOutline;
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

    private static FactionInstinctPolicy.InstinctResult apprenticeOutline(PlayerEntity viewer, PlayerEntity target) {
        WitchPlayerComponent viewerComponent = WitchPlayerComponent.KEY.get(viewer);
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer)) {
            return null;
        }

        boolean self = viewer.getUuid().equals(target.getUuid());
        if (self && viewerComponent.getClairvoyanceSelfTicks() > 0) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_COLOR,
                    false,
                    APPRENTICE_OUTLINE_PRIORITY
            );
        }

        if (!self && viewerComponent.getMurderSenseTicks() > 0 && canMurderSenseHighlight(viewer, target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ApprenticeWitchSkillRules.MURDER_SENSE_COLOR,
                    false,
                    APPRENTICE_OUTLINE_PRIORITY
            );
        }

        if (!self && viewerComponent.getClairvoyanceOthersTicks() > 0 && GameFunctions.isPlayerPlayingAndAlive(target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ApprenticeWitchSkillRules.CLAIRVOYANCE_TARGET_COLOR,
                    false,
                    APPRENTICE_OUTLINE_PRIORITY
            );
        }
        return null;
    }

    private static boolean canMurderSenseHighlight(PlayerEntity viewer, PlayerEntity target) {
        if (!GameFunctions.isPlayerPlayingAndAlive(target)
                || GameFunctions.isPlayerSpectatingOrCreative(target)) {
            return false;
        }
        double range = ApprenticeWitchSkillRules.MURDER_SENSE_RANGE_BLOCKS;
        if (viewer.squaredDistanceTo(target) > range * range) {
            return false;
        }
        return isDangerousHeldItem(target.getMainHandStack()) || isDangerousHeldItem(target.getOffHandStack());
    }

    /**
     * Mirrors Wathe's default killer item instinct targets without granting native killer powers.
     * 同步 wathe 默认杀手物品本能目标，但不授予原生杀手能力。
     */
    private static boolean isDefaultDroppedInstinctTarget(Entity target) {
        return target instanceof ItemEntity
                || target instanceof NoteEntity
                || target instanceof FirecrackerEntity;
    }

    private static boolean isDangerousHeldItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.isIn(WatheItemTags.GUNS)
                || ApprenticeWitchSkillRules.DANGEROUS_ITEM_IDS.contains(Registries.ITEM.getId(stack.getItem())));
    }
}
