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
 * Apprentice-owned instinct decisions. Her own skill outlines (Murder Sense, Clairvoyance) are spell-immune: Fear and
 * Obscure no longer hide them (owner 2026-10-06 D4). The Clairvoyance exposure is public: every living viewer sees an
 * exposed Apprentice in light blue for 10 s (D9), subject to the normal Fear/Obscure suppression.
 * 预备魔女自有的本能判断。她自己的技能描边（杀意感知、千里眼）不受法术影响：恐惧与障眼不再隐藏它们（所有者 2026-10-06 D4）。
 * 千里眼的暴露是公开的：所有存活的观察者都会看到处于暴露中的预备魔女呈淡蓝色，持续 10 秒（D9），并照常受恐惧/障眼压制。
 */
public final class ApprenticeInstinctRules {
    private static final int OUTLINE_PRIORITY = 300;

    private ApprenticeInstinctRules() {
    }

    /** The viewing Apprentice's own skill outline for {@code target}, or null. / 观察中的预备魔女自己的技能描边，没有则为 null。 */
    public static FactionInstinctPolicy.InstinctResult ownSkillHighlight(PlayerEntity viewer, PlayerEntity target) {
        // The window ticks are only ever non-zero for an Apprentice (ApprenticeAbilityRuntime clears them otherwise).
        // 窗口计时只会在预备魔女身上非零（否则 ApprenticeAbilityRuntime 会清空）。
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer) || viewer.getUuid().equals(target.getUuid())) {
            return null;
        }
        WitchPlayerComponent viewerComponent = WitchPlayerComponent.KEY.get(viewer);
        if (viewerComponent.getMurderSenseTicks() > 0 && canMurderSenseHighlight(viewer, target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    MurderSenseAbility.COLOR,
                    false,
                    OUTLINE_PRIORITY
            );
        }
        if (viewerComponent.getClairvoyanceOthersTicks() > 0
                && GameFunctions.isPlayerPlayingAndAlive(target)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    ClairvoyanceAbility.TARGET_COLOR,
                    false,
                    OUTLINE_PRIORITY
            );
        }
        return null;
    }

    /** An exposed Apprentice, seen by every living viewer including herself. / 处于暴露中的预备魔女，所有存活观察者（含她自己）可见。 */
    public static FactionInstinctPolicy.InstinctResult exposureHighlight(PlayerEntity viewer, PlayerEntity target) {
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || GameFunctions.isPlayerSpectatingOrCreative(target)
                || ApprenticePlayerComponent.KEY.get(target).getExposureTicks() <= 0) {
            return null;
        }
        return FactionInstinctPolicy.InstinctResult.show(
                ClairvoyanceAbility.SELF_COLOR,
                false,
                OUTLINE_PRIORITY
        );
    }

    private static boolean canMurderSenseHighlight(PlayerEntity viewer, PlayerEntity target) {
        if (!GameFunctions.isPlayerPlayingAndAlive(target)
                || GameFunctions.isPlayerSpectatingOrCreative(target)) {
            return false;
        }
        double range = MurderSenseAbility.rangeBlocks(ApprenticePlayerComponent.KEY.get(viewer).isGraduated());
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
