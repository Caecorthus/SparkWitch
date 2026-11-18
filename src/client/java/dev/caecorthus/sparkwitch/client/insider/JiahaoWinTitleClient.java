package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.JiahaoTeamWinRules;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Team Jiahao's end title, called by {@code RoundTextRendererJiahaoTitleMixin}. When the neutral win's leading role
 * text is a Team Jiahao member's (an Insider or a Sphinx Hao, or a Corrupt Cop winning a round without a partner,
 * owner D7 2026-11-04), it answers a twin built with the Insider's id, so the Insider's lang keys
 * ({@code announcement.win.insider}, title "嘉豪胜利！"), painted in {@link InsiderRules#TEAM_JIAHAO_COLOR}. The
 * twin is never registered, so the Insider's own role announcement keeps its mint. Only the title changes: Wathe reads
 * the subtitle from the leading row's own role id, so a Sphinx Hao-led win reads {@code game.win.sphinx_hao} and a
 * lone Corrupt Cop win keeps NoellesRoles' subtitle.
 * 嘉豪阵营结算标题，由 {@code RoundTextRendererJiahaoTitleMixin} 调用。中立胜利领衔的职业文本属于嘉豪阵营成员（内应或
 * 狮身豪，或在没有搭档的对局中获胜的黑警，所有者 D7，2026-11-04）时，返回以内应 id 构建的副本，因此使用内应的语言键
 * （{@code announcement.win.insider}，标题“嘉豪胜利！”），并使用 {@link InsiderRules#TEAM_JIAHAO_COLOR}。副本从不注册，
 * 因此内应自己的职业公告仍是薄荷青。只替换标题：Wathe 按领衔行自身的职业 id 读取副标题，因此狮身豪领衔的胜利读取
 * {@code game.win.sphinx_hao}，单独的黑警胜利保留 NoellesRoles 的副标题。
 */
public final class JiahaoWinTitleClient {
    private JiahaoWinTitleClient() {
    }

    public static RoleAnnouncementTexts.RoleAnnouncementText winnerText(RoleAnnouncementTexts.RoleAnnouncementText text) {
        return SparkWitchServerConnection.isConfirmedServer() && isTeamJiahaoTitle(text.identifier)
                ? TeamText.VALUE
                : text;
    }

    /**
     * A neutral win led by a Team Jiahao row is a Team Jiahao win (D7): the Insider and Sphinx Hao win only with their
     * team, and only NoellesRoles' Corrupt Cop branch (wrapped by {@code NoellesRolesJiahaoTeamWinMixin}) names a
     * Corrupt Cop as a neutral winner. With a partner in the round a partner row always leads
     * ({@code GameRoundEndComponentJiahaoTitleMixin}); without one the Corrupt Cop row does.
     * 由嘉豪阵营行领衔的中立胜利就是嘉豪阵营胜利（D7）：内应与狮身豪只会随阵营获胜，且只有 NoellesRoles 的黑警分支（由
     * {@code NoellesRolesJiahaoTeamWinMixin} 包装）会把黑警列为中立胜者。本局有搭档时总由搭档行领衔
     * （{@code GameRoundEndComponentJiahaoTitleMixin}）；没有搭档时由黑警行领衔。
     */
    public static boolean isTeamJiahaoTitle(@Nullable Identifier leadingRoleId) {
        return JiahaoTeamWinRules.Kind.of(leadingRoleId) != null;
    }

    /** Built on first use, on the render thread. / 首次使用时在渲染线程上构建。 */
    private static final class TeamText {
        private static final RoleAnnouncementTexts.RoleAnnouncementText VALUE =
                new RoleAnnouncementTexts.RoleAnnouncementText(InsiderRules.ROLE_ID, InsiderRules.TEAM_JIAHAO_COLOR);
    }
}
