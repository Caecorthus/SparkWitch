package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client name-tag cohort hooks for the Insider, over roles every client already has. The killer listener only ever
 * shows Wathe's fixed red "杀手同伙" line; the mint "嘉豪同伙" label is drawn by {@code InsiderCohortRoleNameMixin}.
 * 内应的客户端名牌同伙钩子，只读取所有客户端都已有的职业信息。杀手监听器只会显示 Wathe 固定的红色“杀手同伙”；
 * 薄荷青“嘉豪同伙”由 {@code InsiderCohortRoleNameMixin} 绘制。
 */
public final class InsiderCohortClientHooks {
    private InsiderCohortClientHooks() {
    }

    /** Wathe {@code ShouldShowCohort} listener: killer looking at the Insider (D3). / 杀手看内应时显示同伙提示（D3）。 */
    public static @Nullable ShouldShowCohort.CohortResult killerCohort(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role targetRole = game.getRole(target);
        // Cheap target check first; trait queries run only when the target is an Insider.
        // 先做廉价的目标判断；只有目标是内应时才查询词条。
        if (!InsiderParticipation.isInsiderRole(targetRole)) {
            return null;
        }
        boolean shows = InsiderCohortRules.killerSeesInsiderCohort(
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                game.canUseKillerFeatures(viewer),
                InsiderSparkTraitsBridge.hasActiveTrait(viewer, InsiderSparkTraitsBridge.IMPOSTOR),
                InsiderSparkTraitsBridge.hasActiveTrait(viewer, InsiderSparkTraitsBridge.CONSCIENCE),
                targetRole,
                GameFunctions.isPlayerPlayingAndAlive(target),
                viewer.getUuid().equals(target.getUuid())
        );
        return shows ? ShouldShowCohort.CohortResult.show(InsiderCohortRules.KILLER_COHORT_PRIORITY) : null;
    }

    /** "嘉豪同伙" pair for the name-tag label (C9). / 名牌标签的“嘉豪同伙”配对（C9）。 */
    public static boolean isJiahaoCohortPair(PlayerEntity viewer, PlayerEntity target) {
        if (!SparkWitchServerConnection.isConfirmedServer() || viewer.getUuid().equals(target.getUuid())) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        return InsiderCohortRules.isJiahaoCohortPair(game.getRole(viewer), game.getRole(target));
    }
}
