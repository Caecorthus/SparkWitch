package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellEchoPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the recommended task row for Wathe's top-left task list ({@code PlannerTaskRowMixin}). Wathe draws that list
 * only for the local player, and the marker comes from the owner-only {@code sparkwitch:planner_master_player} sync,
 * so the client never decides which task is a recommendation; it only restyles the row the server marked, in the
 * planner colour and italic ({@link PlannerTaskRowRules}). Any player can be a target, so this runs for every role.
 * 为 Wathe 左上角任务列表构造推荐任务行（{@code PlannerTaskRowMixin}）。Wathe 只为本地玩家绘制该列表，标记来自仅本人同步的
 * {@code sparkwitch:planner_master_player}，因此客户端从不决定哪项任务是推荐，只以规划大师颜色与斜体改写服务端标记的那一行
 * （{@link PlannerTaskRowRules}）。任何玩家都可能是目标，因此对所有职业生效。
 */
public final class PlannerTaskLine {
    private PlannerTaskLine() {
    }

    /**
     * The replacement row for {@code present}, or {@code null} to keep Wathe's own line (also for a fading row, whose
     * {@code present} is null and which keeps its last text).
     * {@code present} 的替换行；返回 {@code null} 表示保留 Wathe 原文本（淡出中的行 {@code present} 为 null，保留其最后的文本）。
     */
    public static @Nullable Text resolve(@Nullable PlayerMoodComponent.TrainTask present) {
        if (present == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return null;
        }
        PlayerMoodComponent.Task type = present.getType();
        // Fail closed: a missing component marks nothing. / 失败即关闭：缺失组件即无标记。
        PlannerComponent planner = PlannerComponent.KEY.getNullable(player);
        BellEchoPlayerComponent echo = BellEchoPlayerComponent.KEY.getNullable(player);
        if (!PlannerTaskRowRules.restyles(true, true, planner != null && planner.isRecommended(type),
                echo != null && echo.isEchoTask(type))) {
            return null;
        }
        return Text.translatable(PlannerTaskRowRules.taskRowKey(type))
                .styled(style -> style.withColor(PlannerRules.COLOR).withItalic(true));
    }
}
