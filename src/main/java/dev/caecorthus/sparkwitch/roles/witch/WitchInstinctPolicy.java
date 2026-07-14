package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeInstinctRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.FirecrackerEntity;
import dev.doctor4t.wathe.entity.NoteEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.OptionalInt;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * SparkFactionAPI instinct policy for Witch faction outlines and suppression.
 * 魔女阵营轮廓和压制规则的 SparkFactionAPI 本能策略。
 */
public final class WitchInstinctPolicy {
    private static final int GRAND_WITCH_INSTINCT_PRIORITY = WitchFactionRules.INSTINCT_PRIORITY;
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

        Role targetRole = gameComponent.getRole(targetPlayer);
        if (WitchFactionRules.shouldHardSkipInvisiblePhantom(
                viewerRole,
                targetRole,
                targetPlayer.isInvisible()
        )) {
            return FactionInstinctPolicy.InstinctResult.skip(WitchFactionRules.HIDDEN_PHANTOM_SKIP_PRIORITY);
        }

        FactionInstinctPolicy.InstinctResult apprenticeOutline =
                ApprenticeInstinctRules.highlight(viewer, targetPlayer);
        if (apprenticeOutline != null) {
            return apprenticeOutline;
        }

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

    /**
     * Mirrors Wathe's default killer item instinct targets without granting native killer powers.
     * 同步 wathe 默认杀手物品本能目标，但不授予原生杀手能力。
     */
    private static boolean isDefaultDroppedInstinctTarget(Entity target) {
        return target instanceof ItemEntity
                || target instanceof NoteEntity
                || target instanceof FirecrackerEntity;
    }

}
