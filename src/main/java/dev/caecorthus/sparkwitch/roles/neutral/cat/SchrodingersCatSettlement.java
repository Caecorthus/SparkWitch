package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Settlement exclusion (owner 2026-11-03): an untransformed cat never wins and never blocks a win, so every win, block
 * and moment comes out as if it were not in the round. Seams, shared with the Stowaway: every
 * {@code CheckWinCondition} listener through the Stowaway's win-dispatch scope
 * ({@code mixin/cat/GameFunctionsSchrodingersCatSettlementMixin}), the NoellesRoles moment trigger and head counts
 * through {@code FiendWinExclusion.countsAsAlive}, and SparkTraits / SparkStrength through SparkFactionAPI's
 * {@code SettlementExclusions}. Unlike the Stowaway it never joins the winners: every round-end row writer already
 * gives a neutral row {@code isWinner=false}. The raw role is read, so a transformed cat simply counts as its new role.
 * 结算排除（所有者 2026-11-03）：未转化的猫永不获胜、也不阻挡任何胜利，因此所有胜利、阻止与时刻的结果都与它不在本局时
 * 相同。接缝与偷渡客共用：所有 {@code CheckWinCondition} 监听器经由偷渡客的胜负分发作用域
 * （{@code mixin/cat/GameFunctionsSchrodingersCatSettlementMixin}），NoellesRoles 时刻触发与人数计数经由
 * {@code FiendWinExclusion.countsAsAlive}，SparkTraits / SparkStrength 经由 SparkFactionAPI 的
 * {@code SettlementExclusions}。与偷渡客不同，它从不加入获胜方：所有结算行写入方本就给中立行 {@code isWinner=false}。
 * 读取原始职业，因此转化后的猫直接按新身份计算。
 */
public final class SchrodingersCatSettlement {
    private SchrodingersCatSettlement() {
    }

    /** Pure: only the raw cat role is excluded. / 纯规则：只排除原始职业为猫者。 */
    public static boolean isExcludedRole(@Nullable Role rawRole) {
        return SchrodingersCatService.isCatRole(rawRole);
    }

    /**
     * Whether this player's raw role is the cat; false for a null player or a world without Wathe's game component.
     * 该玩家的原始职业是否为猫；玩家为 null 或世界没有 Wathe 对局组件时为 false。
     */
    public static boolean isExcluded(@Nullable PlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
        return game != null && isExcludedRole(game.getRole(player));
    }
}
