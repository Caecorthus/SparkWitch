package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Apprentice-owned instinct decisions for Murder Sense and Clairvoyance.
 * 预备魔女自有的本能判断，集中管理杀意感知和千里眼描边。
 */
public final class ApprenticeInstinctRules {
    private static final int OUTLINE_PRIORITY = 300;

    private ApprenticeInstinctRules() {
    }

    public static FactionInstinctPolicy.InstinctResult highlight(PlayerEntity viewer, PlayerEntity target) {
        WitchPlayerComponent viewerComponent = WitchPlayerComponent.KEY.get(viewer);
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer)) {
            return null;
        }

        boolean self = viewer.getUuid().equals(target.getUuid());
        if (self && viewerComponent.getClairvoyanceSelfTicks() > 0) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ClairvoyanceAbility.SELF_COLOR,
                    false,
                    OUTLINE_PRIORITY
            );
        }
        if (!self && viewerComponent.getMurderSenseTicks() > 0 && canMurderSenseHighlight(viewer, target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    MurderSenseAbility.COLOR,
                    false,
                    OUTLINE_PRIORITY
            );
        }
        if (!self && viewerComponent.getClairvoyanceOthersTicks() > 0
                && GameFunctions.isPlayerPlayingAndAlive(target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ClairvoyanceAbility.TARGET_COLOR,
                    false,
                    OUTLINE_PRIORITY
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

    private static boolean isDangerousHeldItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.isIn(WatheItemTags.GUNS)
                || MurderSenseAbility.DANGEROUS_ITEM_IDS.contains(Registries.ITEM.getId(stack.getItem())));
    }
}
