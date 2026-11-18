package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen Insider and Team Jiahao predicates, safe on client and server: they read only Wathe's synced game component.
 * Roles are exact-id gates, never inferred from faction or namespace. A SparkWitch partner is an Insider or a Sphinx
 * Hao: a round with a partner runs the Team Jiahao co-win rules ({@link JiahaoTeamWinRules}). Insider-only features
 * (kit, doors, shop, money, instinct) keep {@link #isInsiderRole} / {@link #isInsider}.
 * 冻结的内应与嘉豪阵营判断，客户端与服务端均可调用：只读取 Wathe 已同步的对局组件。职业按精确 id 判断，
 * 从不由阵营或命名空间推断。SparkWitch 搭档即内应或狮身豪：有搭档的对局启用嘉豪阵营共同胜利规则
 * （{@link JiahaoTeamWinRules}）。内应专属功能（开局物品、门、商店、金钱、本能）仍使用 {@link #isInsiderRole} /
 * {@link #isInsider}。
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

    /** A Sniper: a Team Jiahao member that is not one of the Corrupt Cop's partners. / 狙击手：嘉豪阵营成员，但不是黑警搭档。 */
    public static boolean isSniperRole(@Nullable Role role) {
        return dev.caecorthus.sparkwitch.roles.neutral.sniper.SniperRules.isSniperRole(role);
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
        return isPartnerRole(role) || isCorruptCopRole(role)
                || dev.caecorthus.sparkwitch.roles.neutral.sniper.SniperRules.isSniperRole(role);
    }

    public static boolean isInsider(@Nullable PlayerEntity player) {
        return player != null && isInsiderRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isTeamMember(@Nullable PlayerEntity player) {
        return player != null && isTeamRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
