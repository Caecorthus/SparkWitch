package dev.caecorthus.sparkwitch.client.mixin.insider;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.insider.JiahaoWinTitleClient;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Team Jiahao end title in the faction's own color (owner 2026-10-05). Wathe ({@code 1.5.6-spark-1.21.1}) titles a
 * neutral win with the {@code winText} of the first winning round-end row's role, looked up through
 * {@code RoleAnnouncementTexts.getForRole(Identifier)}, the only such call in {@code renderHud}; a Team Jiahao win
 * always leads with an Insider row ({@code GameRoundEndComponentJiahaoTitleMixin}). This modifies only that lookup's
 * result, so SparkFactionAPI's {@code @Redirect} on the {@code winText} read right after it keeps working: a custom
 * faction win still shows SparkFactionAPI's title, otherwise it reads the Team Jiahao text. The subtitle path and the
 * player cards are untouched.
 * 嘉豪阵营结算标题使用阵营自己的颜色（所有者 2026-10-05）。Wathe（{@code 1.5.6-spark-1.21.1}）以第一条获胜结算行职业的
 * {@code winText} 作为中立胜利标题，经由 {@code RoleAnnouncementTexts.getForRole(Identifier)} 查找，这是
 * {@code renderHud} 中唯一的此类调用；嘉豪阵营胜利总由内应行领衔（{@code GameRoundEndComponentJiahaoTitleMixin}）。
 * 本 mixin 只修改该查找的结果，因此 SparkFactionAPI 紧随其后对 {@code winText} 读取的 {@code @Redirect} 照常生效：
 * 自定义阵营胜利仍显示 SparkFactionAPI 的标题，否则读取嘉豪阵营文本。副标题路径与玩家卡片不受影响。
 */
@Mixin(RoundTextRenderer.class)
public abstract class RoundTextRendererJiahaoTitleMixin {
    @ModifyExpressionValue(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/RoleAnnouncementTexts;getForRole(Lnet/minecraft/util/Identifier;)Ldev/doctor4t/wathe/client/gui/RoleAnnouncementTexts$RoleAnnouncementText;"
            ),
            require = 1,
            allow = 1
    )
    private static RoleAnnouncementTexts.RoleAnnouncementText sparkwitch$teamJiahaoWinTitle(
            RoleAnnouncementTexts.RoleAnnouncementText winnerText
    ) {
        return JiahaoWinTitleClient.winnerText(winnerText);
    }
}
