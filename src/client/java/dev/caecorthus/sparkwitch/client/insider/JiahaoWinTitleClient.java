package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Team Jiahao's end title, called by {@code RoundTextRendererJiahaoTitleMixin}. When the neutral win's leading role
 * text is the Insider's, it answers a twin built with the same id, so the same lang keys
 * ({@code announcement.win.insider}, title "嘉豪胜利！"), but painted in {@link InsiderRules#TEAM_JIAHAO_COLOR}
 * instead of the Insider's mint. The twin is never registered, so the Insider's own role announcement keeps its mint.
 * 嘉豪阵营结算标题，由 {@code RoundTextRendererJiahaoTitleMixin} 调用。中立胜利领衔的职业文本是内应时，返回以同一 id
 * 构建的副本，因此语言键相同（{@code announcement.win.insider}，标题“嘉豪胜利！”），但使用
 * {@link InsiderRules#TEAM_JIAHAO_COLOR} 而不是内应的薄荷青。副本从不注册，因此内应自己的职业公告仍是薄荷青。
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
     * A neutral win led by an Insider row is a Team Jiahao win: the Insider wins only with its team.
     * 由内应行领衔的中立胜利就是嘉豪阵营胜利：内应只会随阵营获胜。
     */
    public static boolean isTeamJiahaoTitle(@Nullable Identifier leadingRoleId) {
        return InsiderRules.ROLE_ID.equals(leadingRoleId);
    }

    /** Built on first use, on the render thread. / 首次使用时在渲染线程上构建。 */
    private static final class TeamText {
        private static final RoleAnnouncementTexts.RoleAnnouncementText VALUE =
                new RoleAnnouncementTexts.RoleAnnouncementText(InsiderRules.ROLE_ID, InsiderRules.TEAM_JIAHAO_COLOR);
    }
}
