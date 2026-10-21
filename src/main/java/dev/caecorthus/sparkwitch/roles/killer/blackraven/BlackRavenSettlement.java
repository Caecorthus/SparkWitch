package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Settlement exclusion (plan v2 §3.3, owner 2026-10-21): the Black Raven NEVER counts as a living player for round
 * settlement, with no special case, so every win, block and moment comes out exactly as if the Raven were not in the
 * round. Keyed on the RAW role ({@code GameWorldComponent#getRole}); the disguise overlay widens only {@code isRole}.
 * Callers keep their own alive test and skip the Raven on top of it. SparkWitch's seams: Wathe's killer-win civilian
 * check and civilian-death time bonus ({@code mixin/blackraven/}), the Witch and Murderous Witch counts, the
 * NoellesRoles counts behind {@code FiendWinExclusion.countsAsAlive}, and the NoellesRoles Pathogen win. Other mods read
 * the same role through SparkFactionAPI's {@code SettlementExclusions}, into which SparkWitch registers the Raven
 * ({@code compat/SparkFactionSettlementExclusionCompat}).
 * 结算排除（计划 v2 §3.3，所有者 2026-10-21）：黑羽鸦在回合结算中永远不算存活玩家，没有任何特例，因此所有胜利、阻止与
 * 时刻的结果都与黑羽鸦不在本局时完全相同。按原始职业（{@code GameWorldComponent#getRole}）判断；伪装覆盖层只放宽
 * {@code isRole}。调用方保留自身的存活判断，并在其之上跳过黑羽鸦。SparkWitch 的接缝：Wathe 杀手胜利的好人存活判断与好人
 * 死亡加时（{@code mixin/blackraven/}）、魔女与杀意魔女的计数、经由 {@code FiendWinExclusion.countsAsAlive} 的
 * NoellesRoles 计数，以及 NoellesRoles 病原体胜利。其他模组通过 SparkFactionAPI 的 {@code SettlementExclusions} 读取同一
 * 职业，SparkWitch 会把黑羽鸦注册进去（{@code compat/SparkFactionSettlementExclusionCompat}）。
 */
public final class BlackRavenSettlement {
    private BlackRavenSettlement() {
    }

    /** Pure: only the raw Black Raven role is excluded. / 纯规则：只排除原始职业为黑羽鸦者。 */
    public static boolean isExcludedRole(@Nullable Role rawRole) {
        return BlackRavenRules.isBlackRaven(rawRole);
    }

    /**
     * Whether this player's raw role is excluded from settlement; false for a null player or a world without Wathe's
     * game component. Never calls {@code isRole}.
     * 该玩家的原始职业是否被排除出结算；玩家为 null 或世界没有 Wathe 对局组件时为 false。从不调用 {@code isRole}。
     */
    public static boolean isExcluded(@Nullable PlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
        return game != null && isExcludedRole(game.getRole(player));
    }

    /**
     * Keeps the host's alive answer, except that the Black Raven never counts. Dead players skip the role lookup.
     * 保留宿主的存活判断结果，唯独黑羽鸦永不计入。已死亡玩家不会进行职业判断。
     */
    public static boolean countsAsAlive(boolean hostAlive, @Nullable PlayerEntity player) {
        return hostAlive && !isExcluded(player);
    }
}
