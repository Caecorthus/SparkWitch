package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeInstinctRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserRules;
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
            GameWorldComponent gameComponent,
            boolean confirmedServer
    ) {
        Role viewerRole = gameComponent.getRole(viewer);
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(viewer);
        FactionInstinctPolicy.InstinctResult restrictedWraith = restrictedWraithHighlight(wraith.isRestricted());
        if (restrictedWraith != null) {
            return restrictedWraith;
        }
        boolean viewerAlive = GameFunctions.isPlayerPlayingAndAlive(viewer);
        boolean viewerSpectatingOrCreative = GameFunctions.isPlayerSpectatingOrCreative(viewer);
        boolean curserViewer = viewerRole == SparkWitchRoles.curser();
        boolean curserOutlineEligible = CurserRules.canParticipateInPlayerOutlines(
                confirmedServer,
                gameComponent.isRunning(),
                curserViewer,
                WraithStateService.isActive(viewer),
                WraithStateService.isPromoted(viewer),
                CurserPlayerComponent.KEY.get(viewer).isConfused(),
                viewerSpectatingOrCreative
        );
        // A living Rift Gate occupant is a spectator but keeps this instinct; see shouldUseCustomInstinctHighlight.
        // 存活的裂隙门内玩家虽是旁观者，仍保留此本能；见 shouldUseCustomInstinctHighlight。
        if (curserViewer ? !curserOutlineEligible : !WitchFactionRules.shouldUseCustomInstinctHighlight(viewerAlive)) {
            return null;
        }
        // The Apprentice's own skill outlines are spell-immune, so they answer before Obscure (owner 2026-10-06 D4).
        // 预备魔女自己的技能描边不受法术影响，因此在障眼之前作答（所有者 2026-10-06 D4）。
        if (target instanceof PlayerEntity apprenticeTarget) {
            FactionInstinctPolicy.InstinctResult apprenticeOutline =
                    ApprenticeInstinctRules.ownSkillHighlight(viewer, apprenticeTarget);
            if (apprenticeOutline != null) {
                return apprenticeOutline;
            }
        }
        if (WitchFactionRules.shouldObscureInstinct(
                WitchWorldComponent.KEY.get(viewer.getWorld()).isInstinctObscured(),
                viewerRole,
                viewerAlive
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

        FactionInstinctPolicy.InstinctResult exposureOutline =
                ApprenticeInstinctRules.exposureHighlight(viewer, targetPlayer);
        if (exposureOutline != null) {
            return exposureOutline;
        }

        OptionalInt color = WitchFactionRules.instinctColor(viewerRole, targetRole);
        if (color.isEmpty()) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
            return FactionInstinctPolicy.InstinctResult.skip(GRAND_WITCH_INSTINCT_PRIORITY);
        }
        // Ordinary instinct owns its color; factor knowledge is a client-only last fallback.
        // 普通本能保留自身颜色；因子知识仅由客户端最后补缺。
        return FactionInstinctPolicy.InstinctResult.show(color.getAsInt(), true, GRAND_WITCH_INSTINCT_PRIORITY);
    }

    static FactionInstinctPolicy.InstinctResult restrictedWraithHighlight(boolean restricted) {
        return restricted ? FactionInstinctPolicy.InstinctResult.skip(OBSCURE_SKIP_PRIORITY) : null;
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
