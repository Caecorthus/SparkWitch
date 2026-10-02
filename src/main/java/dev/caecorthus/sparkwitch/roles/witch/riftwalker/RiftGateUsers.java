package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server helper that feeds {@link RiftGateUser#classify} from live state: the RAW Wathe role
 * ({@code GameWorldComponent#getRole}, never the Black Raven acting role) and the SparkFactionAPI effective faction
 * (an unknown or null faction counts as NOT witch faction). Classification only: liveness, participation, stun and
 * other entry gates belong to the caller.
 * 服务端辅助：以实时状态为 {@link RiftGateUser#classify} 提供输入——原始 Wathe 职业（{@code GameWorldComponent#getRole}，
 * 从不使用黑羽鸦伪装职业）与 SparkFactionAPI 有效阵营（未知或 null 视为非魔女阵营）。只负责分类：存活、参赛、眩晕等
 * 进门门槛由调用方负责。
 */
public final class RiftGateUsers {
    private RiftGateUsers() {
    }

    public static RiftGateUser classify(PlayerEntity player) {
        if (player == null) {
            return RiftGateUser.NONE;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (role == null) {
            return RiftGateUser.NONE;
        }
        return RiftGateUser.classify(
                RiftwalkerRules.isRiftwalker(role),
                isWitchFaction(player, game),
                role == SparkWitchRoles.murderousWitch(),
                role == SparkWitchRoles.apprenticeWitch());
    }

    /**
     * Effective faction is exactly {@code sparkwitch:witch} (C6: the Sabbath pulls only these; the Apprentice and
     * Murderous Witches are not same faction). Unknown → false.
     * 有效阵营恰为 {@code sparkwitch:witch}（C6：魔女集会只召集这些人；预备魔女与杀意魔女不算同阵营）。未知 → false。
     */
    public static boolean isWitchFaction(PlayerEntity player) {
        return player != null && isWitchFaction(player, GameWorldComponent.KEY.get(player.getWorld()));
    }

    private static boolean isWitchFaction(PlayerEntity player, GameWorldComponent game) {
        Identifier faction = SparkFactionApi.resolveEffectiveFaction(player, game);
        return SparkWitchFactions.WITCH.equals(faction);
    }
}
