package dev.caecorthus.sparkwitch.client.bellringer;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellEchoPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the red "must" line for Wathe's top-left task list. Wathe renders that list only for the local
 * player, and the Echo marker/countdown come from the owner-only {@code sparkwitch:bell_echo} sync, so the
 * client never decides which task is forced; it only restyles what the server marked.
 * 为 Wathe 左上角任务列表构造红色“必须”文本行。Wathe 只为本地玩家渲染该列表，回响标记与倒计时来自
 * 仅拥有者同步的 {@code sparkwitch:bell_echo}，因此客户端从不决定哪项任务被强制，只重设服务端已标记任务的样式。
 */
public final class BellEchoTaskLine {
    private BellEchoTaskLine() {
    }

    /**
     * Returns the replacement text for the Echo task, or {@code null} to keep Wathe's own line.
     * 返回回响任务的替换文本；返回 {@code null} 表示保留 Wathe 原文本。
     */
    public static @Nullable Text resolve(@Nullable PlayerMoodComponent.TrainTask task) {
        if (task == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return null;
        }
        BellEchoPlayerComponent echo = BellEchoPlayerComponent.KEY.get(player);
        if (!echo.isEchoTask(task.getType())) {
            return null;
        }
        return Text.translatable(
                BellEchoHudRules.taskKey(task.getName()),
                BellEchoHudRules.taskSeconds(echo.echoRemainingTicks())
        ).formatted(Formatting.RED);
    }
}
