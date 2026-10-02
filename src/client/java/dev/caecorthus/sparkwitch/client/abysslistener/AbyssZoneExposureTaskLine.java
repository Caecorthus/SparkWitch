package dev.caecorthus.sparkwitch.client.abysslistener;

import dev.caecorthus.sparkwitch.client.mixin.abysslistener.MoodRendererTaskRowAbyssAccessor;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkZoneStandingDrain;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import java.util.Collection;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The display-only pseudo task 「快离开这里！！！」 (owner D7) in Wathe's top-left task list. It is drawn while the LOCAL
 * player's owner-synced {@code sparkwitch:abyss_zone_exposure} is set; the client never decides exposure. It is never
 * put into Wathe's task map, so it cannot be completed, fires no {@code TaskComplete}, pays nothing and never touches
 * the Bell Ringer task hold. It joins Wathe's layout as the last row (also with zero real tasks), so the mood bar
 * moves below it, and fades like a Wathe row.
 * 仅用于显示的临时任务「快离开这里！！！」（所有者 D7），位于 Wathe 左上角任务列表。只在本地玩家仅同步给本人的
 * {@code sparkwitch:abyss_zone_exposure} 生效时绘制；客户端从不判定暴露。它从不进入 Wathe 任务表，因此无法完成、不触发
 * {@code TaskComplete}、不给任何奖励，也不影响敲钟人对任务生成的接管。它作为最后一行加入 Wathe 布局（没有真实任务时同样如此），
 * 理智条随之移到其下方，并像 Wathe 的任务行一样淡入淡出。
 * The fade is static client state, so {@link #settleFrame} drops it after any frame Wathe's layout skipped while the
 * local player is not exposed, and {@link #reset} drops it on every disconnect and join; a stale fade therefore never
 * flashes the line in a new round or on another server.
 * 淡入淡出是静态客户端状态：本地玩家未暴露时，只要某一帧跳过了 Wathe 布局，{@link #settleFrame} 就丢弃它；每次断开或加入连接时
 * {@link #reset} 也会丢弃它。因此过期的淡入淡出永远不会让本行在新一局或其他服务器上闪现。
 */
public final class AbyssZoneExposureTaskLine {
    public static final String TRANSLATION_KEY = "task.sparkwitch.abyss_zone_exposure";

    private static float alpha;
    /** Wathe's layout path reached the line since the last {@link #settleFrame}. / 自上次结算以来 Wathe 布局是否运行过本行。 */
    private static boolean laidOut;

    private AbyssZoneExposureTaskLine() {
    }

    /** Synced exposure of the local player on a confirmed SparkWitch server. / 已确认 SparkWitch 服务端上本地玩家的暴露。 */
    public static boolean isVisible(@Nullable PlayerEntity player) {
        return player != null
                && SparkWitchServerConnection.isConfirmedServer()
                && DeepDarkZoneStandingDrain.isExposed(player);
    }

    /**
     * Whether the pseudo line occupies a row this frame (shown or still fading), which keeps Wathe's mood bar visible
     * like any task row does.
     * 本帧临时任务行是否占据一行（正在显示或仍在淡出），与其他任务行一样让 Wathe 的理智条保持可见。
     */
    public static boolean occupiesRow(@Nullable PlayerEntity player) {
        return isVisible(player) || AbyssZoneExposureTaskLineRules.inLayout(alpha);
    }

    /**
     * Once per HUD frame from a path that always runs ({@code HudRenderCallback}, also under F1 and while Wathe's own
     * HUD returns early): drops the fade when Wathe's layout path did not reach the line since the previous frame
     * (death, spectating, round over, psycho mode, train HUD off) and the local player is not exposed. While the layout
     * keeps running, the line still fades out like a Wathe row.
     * 每个 HUD 帧从一条总会执行的路径调用一次（{@code HudRenderCallback}，F1 下以及 Wathe 自身 HUD 提前返回时同样执行）：
     * 若自上一帧以来 Wathe 布局未运行本行（死亡、旁观、对局结束、疯魔模式、列车 HUD 关闭）且本地玩家未暴露，则丢弃淡入淡出。
     * 布局持续运行期间，本行仍像 Wathe 任务行一样淡出。
     */
    public static void settleFrame(@Nullable PlayerEntity player) {
        if (AbyssZoneExposureTaskLineRules.dropsStaleFade(laidOut, isVisible(player))) {
            alpha = 0.0F;
        }
        laidOut = false;
    }

    /** Disconnect or join: no fade survives a connection. / 断开或加入连接：淡入淡出状态不跨连接保留。 */
    public static void reset() {
        alpha = 0.0F;
        laidOut = false;
    }

    /**
     * Runs right after Wathe settled this frame's {@code moodOffset} and {@code moodTextWidth} and before it draws the
     * mood icon and bar: fades the line, draws it below the last Wathe row, then re-targets those two public layout
     * fields at the line (see {@link AbyssZoneExposureTaskLineRules#moodOffsetCorrection}).
     * 在 Wathe 确定本帧 {@code moodOffset} 与 {@code moodTextWidth} 之后、绘制理智图标与理智条之前运行：更新淡入淡出，
     * 在最后一个 Wathe 行下方绘制本行，再把这两个公开布局字段的目标改为本行（见
     * {@link AbyssZoneExposureTaskLineRules#moodOffsetCorrection}）。
     */
    public static void renderAfterLayout(
            PlayerEntity player,
            TextRenderer textRenderer,
            DrawContext context,
            float delta,
            Collection<?> watheRows
    ) {
        laidOut = true;
        alpha = AbyssZoneExposureTaskLineRules.nextAlpha(alpha, delta, isVisible(player));
        if (!AbyssZoneExposureTaskLineRules.inLayout(alpha)) {
            return;
        }
        boolean hasWatheRow = false;
        float maxOffset = 0.0F;
        Text lastText = null;
        for (Object row : watheRows) {
            MoodRendererTaskRowAbyssAccessor access = (MoodRendererTaskRowAbyssAccessor) row;
            float offset = access.sparkwitch$getAbyssRowOffset();
            if (!hasWatheRow || offset > maxOffset) {
                hasWatheRow = true;
                maxOffset = offset;
                lastText = access.sparkwitch$getAbyssRowText();
            }
        }
        Text line = Text.translatable(TRANSLATION_KEY);
        float lineOffset = AbyssZoneExposureTaskLineRules.lineOffset(hasWatheRow, maxOffset);
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, AbyssZoneExposureTaskLineRules.LINE_HEIGHT * lineOffset, 0.0F);
        context.drawTextWithShadow(
                textRenderer,
                line,
                AbyssZoneExposureTaskLineRules.TEXT_X,
                AbyssZoneExposureTaskLineRules.FIRST_LINE_Y,
                AbyssZoneExposureTaskLineRules.argb(AbyssZoneExposureTaskLineRules.color(player.age), alpha));
        context.getMatrices().pop();

        MoodRenderer.moodOffset += AbyssZoneExposureTaskLineRules.moodOffsetCorrection(delta, hasWatheRow, maxOffset);
        float lastWidth = lastText == null ? 0.0F : textRenderer.getWidth(lastText);
        MoodRenderer.moodTextWidth += AbyssZoneExposureTaskLineRules.moodTextWidthCorrection(
                delta, hasWatheRow, lastWidth, textRenderer.getWidth(line));
    }
}
