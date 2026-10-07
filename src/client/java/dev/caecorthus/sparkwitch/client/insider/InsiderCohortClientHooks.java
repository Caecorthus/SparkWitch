package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client name-tag cohort hook for the Insider, over roles every client already has. The gold "嘉豪同伙" label is drawn
 * by {@code InsiderCohortRoleNameMixin}; killers get no cohort line on the Insider.
 * 内应的客户端名牌同伙钩子，只读取所有客户端都已有的职业信息。金色“嘉豪同伙”由 {@code InsiderCohortRoleNameMixin}
 * 绘制；杀手看内应时没有同伙提示。
 */
public final class InsiderCohortClientHooks {
    private InsiderCohortClientHooks() {
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
