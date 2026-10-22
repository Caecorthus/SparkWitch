package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side Echo audience and participant checks.
 * 服务端回响受众与参与者判断。
 */
public final class BellRingerEchoTargeting {
    private BellRingerEchoTargeting() {
    }

    /**
     * Living, non-spectator, non-creative, non-active-Wraith player with a role
     * ({@link RoundParticipants#ROLE_HOLDER}, the player's own world).
     * 存活、非旁观、非创造、非激活冤魂且拥有职业的玩家（{@link RoundParticipants#ROLE_HOLDER}，读取玩家自身世界）。
     */
    public static boolean isParticipant(ServerPlayerEntity player) {
        return RoundParticipants.ROLE_HOLDER.test(player);
    }

    /**
     * Base mood not NONE and effective faction civilian (approximates SparkTraits' effective REAL mood).
     * Base mood is read from the role itself, never from SparkTraits' mood redirect; the effective faction
     * comes from SparkFactionAPI, so Conscience killers count and Impostor civilians do not.
     * 基础情绪非 NONE 且有效阵营为平民（近似 SparkTraits 的有效真实情绪）。基础情绪直接读取职业本身，
     * 不经过 SparkTraits 的情绪重定向；有效阵营来自 SparkFactionAPI，因此良知杀手计入、冒名平民不计入。
     */
    public static boolean hasRealSanity(ServerPlayerEntity player, GameWorldComponent game) {
        Role role = game.getRole(player);
        if (role == null) {
            return false;
        }
        return BellRingerRules.classify(
                true,
                role.getMoodType(),
                SparkFactionApi.resolveEffectiveFaction(player, game),
                true
        ) == BellRingerRules.Audience.AFFECTED;
    }

    /**
     * Classifies {@code player} for a cast by {@code ringer}; the ringer itself is always a hearer.
     * SparkFactionAPI's structural affect veto is honoured: a vetoed player with real sanity only hears.
     * 按 {@code ringer} 的施法对 {@code player} 分类；敲钟人本人始终为听者。
     * 遵守 SparkFactionAPI 的结构性影响否决：被否决的有理智玩家只会听到钟声。
     */
    public static BellRingerRules.Audience audience(
            ServerPlayerEntity ringer,
            ServerPlayerEntity player,
            GameWorldComponent game
    ) {
        if (player == ringer || player.getUuid().equals(ringer.getUuid())) {
            return BellRingerRules.Audience.HEARER;
        }
        if (!isParticipant(player)) {
            return BellRingerRules.Audience.NONE;
        }
        Role role = game.getRole(player);
        return BellRingerRules.classify(
                true,
                role == null ? null : role.getMoodType(),
                SparkFactionApi.resolveEffectiveFaction(player, game),
                SparkFactionApi.canAffectPlayer(ringer, player, BellRingerRules.ECHO_SKILL_ID, game)
        );
    }
}
