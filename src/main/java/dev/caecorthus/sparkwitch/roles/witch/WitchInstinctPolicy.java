package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.FirecrackerEntity;
import dev.doctor4t.wathe.entity.NoteEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import java.util.OptionalInt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * SparkFactionAPI instinct policy for Witch faction outlines and suppression.
 * 魔女阵营轮廓和压制规则的 SparkFactionAPI 本能策略。
 */
public final class WitchInstinctPolicy {
    private static final int GRAND_WITCH_INSTINCT_PRIORITY = WitchFactionRules.INSTINCT_PRIORITY;
    private static final int APPRENTICE_OUTLINE_PRIORITY = 300;
    private static final int OBSCURE_SKIP_PRIORITY = 1_000;

    private WitchInstinctPolicy() {
    }

    static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        Role viewerRole = gameComponent.getRole(viewer);
        boolean viewerAlive = GameFunctions.isPlayerPlayingAndAlive(viewer);
        boolean viewerSpectatingOrCreative = GameFunctions.isPlayerSpectatingOrCreative(viewer);
        if (!WitchFactionRules.shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)) {
            return null;
        }
        if (WitchFactionRules.shouldObscureInstinct(
                WitchWorldComponent.KEY.get(viewer.getWorld()).isInstinctObscured(),
                viewerRole,
                viewerAlive,
                viewerSpectatingOrCreative
        )) {
            return FactionInstinctPolicy.InstinctResult.skip(OBSCURE_SKIP_PRIORITY);
        }

        if (isDefaultDroppedInstinctTarget(target)) {
            OptionalInt droppedItemColor = WitchFactionRules.droppedItemInstinctColor(viewerRole);
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
        OptionalInt color = WitchFactionRules.instinctColor(viewerRole, targetRole);
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
                    ClairvoyanceAbility.SELF_COLOR,
                    false,
                    APPRENTICE_OUTLINE_PRIORITY
            );
        }

        if (!self && viewerComponent.getMurderSenseTicks() > 0 && canMurderSenseHighlight(viewer, target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    MurderSenseAbility.COLOR,
                    false,
                    APPRENTICE_OUTLINE_PRIORITY
            );
        }

        if (!self && viewerComponent.getClairvoyanceOthersTicks() > 0 && GameFunctions.isPlayerPlayingAndAlive(target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ClairvoyanceAbility.TARGET_COLOR,
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
        double range = MurderSenseAbility.RANGE_BLOCKS;
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
                || MurderSenseAbility.DANGEROUS_ITEM_IDS.contains(Registries.ITEM.getId(stack.getItem())));
    }
}
