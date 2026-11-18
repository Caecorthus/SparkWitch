package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.roles.neutral.sniper.SniperRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen Insider and Team Jiahao predicates, safe on client and server: they read only Wathe's synced game component.
 * Roles are exact-id gates, never inferred from faction or namespace. {@link #isPartnerRole} is an Insider or a Sphinx
 * Hao; the Sniper is its own kind ({@link #isSniperRole}). Any of the three turns the Team Jiahao co-win rules on
 * ({@link JiahaoTeamWinRules.Kind#partner}), and each fills the lineup's one partner seat beside the Corrupt Cop
 * ({@link JiahaoThirdFactionLineup}), though only the Insider and Sphinx Hao are ever drawn there. Insider-only
 * features (kit, doors, shop, money, instinct) keep {@link #isInsiderRole} / {@link #isInsider}.
 * 冻结的内应与嘉豪阵营判断，客户端与服务端均可调用：只读取 Wathe 已同步的对局组件。职业按精确 id 判断，
 * 从不由阵营或命名空间推断。{@link #isPartnerRole} 即内应或狮身豪；狙击手是单独的类别（{@link #isSniperRole}）。三者中
 * 任意一个都会启用嘉豪阵营共同胜利规则（{@link JiahaoTeamWinRules.Kind#partner}），也都占用阵容中黑警旁唯一的同伴席位
 * （{@link JiahaoThirdFactionLineup}），但只有内应与狮身豪会被抽到该席位。内应专属功能（开局物品、门、商店、金钱、本能）
 * 仍使用 {@link #isInsiderRole} / {@link #isInsider}。
 */
public final class InsiderParticipation {
    private InsiderParticipation() {
    }

    public static boolean isInsiderRole(@Nullable Role role) {
        return role != null && InsiderRules.ROLE_ID.equals(role.identifier());
    }

    public static boolean isCorruptCopRole(@Nullable Role role) {
        return role != null && InsiderRules.CORRUPT_COP_ID.equals(role.identifier());
    }

    /**
     * A Sniper: a Team Jiahao member kept out of {@link #isPartnerRole}; callers that also mean it (team membership, the
     * lineup's partner seat, the instinct views) test it explicitly. / 狙击手：嘉豪阵营成员，不计入
     * {@link #isPartnerRole}；同样需要它的调用方（阵营成员、阵容同伴席位、本能视角）会显式判断。
     */
    public static boolean isSniperRole(@Nullable Role role) {
        return SniperRules.isSniperRole(role);
    }

    public static boolean isSphinxHaoRole(@Nullable Role role) {
        return SphinxHaoRules.isSphinxHao(role);
    }

    /**
     * SparkWitch partner of the Corrupt Cops: an Insider or a Sphinx Hao. / 黑警的 SparkWitch 搭档：内应或狮身豪。
     */
    public static boolean isPartnerRole(@Nullable Role role) {
        return isInsiderRole(role) || isSphinxHaoRole(role);
    }

    /**
     * Team Jiahao: every Corrupt Cop, every Insider (D2), every Sphinx Hao and every Sniper (owner 2026-11-17).
     * 嘉豪阵营：所有黑警、所有内应（D2）、所有狮身豪与所有狙击手（所有者 2026-11-17）。
     */
    public static boolean isTeamRole(@Nullable Role role) {
        return isPartnerRole(role) || isCorruptCopRole(role) || isSniperRole(role);
    }

    public static boolean isInsider(@Nullable PlayerEntity player) {
        return player != null && isInsiderRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isTeamMember(@Nullable PlayerEntity player) {
        return player != null && isTeamRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
