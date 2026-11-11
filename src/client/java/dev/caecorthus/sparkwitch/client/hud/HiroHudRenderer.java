package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 二阶堂希罗's role-owned HUD, drawn from the {@code HudRenderCallback} that {@code HiroClientModule} registers.
 * SCAFFOLD: the visibility gate is final (hidden unless the local player's raw synced role is Hiro, alive and playing,
 * on a confirmed SparkWitch server), the body is empty. The 死亡回归 line (ready / window / cooldown, lang group
 * {@code hud.sparkwitch.nikaido_hiro.return.*}) and the 魔女化 progress are in progress; when they claim rows in the
 * shared bottom-right corner, reserve them in {@code SaintHudLayoutRules} (no row is reserved yet). Mana is not drawn
 * here: the shared top-right {@code WitchManaHudRenderer} row already shows it for every mana role.
 * 二阶堂希罗的职业自有 HUD，由 {@code HiroClientModule} 注册的 {@code HudRenderCallback} 绘制。骨架阶段：显示门槛已定（仅当本地
 * 玩家真实同步身份为希罗、存活且参与对局，并处于已确认的 SparkWitch 服务器时显示），绘制内容为空。死亡回归提示行（就绪 / 窗口 /
 * 冷却，语言组 {@code hud.sparkwitch.nikaido_hiro.return.*}）与魔女化进度仍在实现中；它们占用右下角共享行位时，需在
 * {@code SaintHudLayoutRules} 中预留（目前未预留任何行）。魔力不在此绘制：右上角共享的 {@code WitchManaHudRenderer} 已为所有魔力
 * 职业显示。
 */
public final class HiroHudRenderer {
    private HiroHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isActiveHiro(player)) {
            return;
        }
        // TODO(死亡回归 / 魔女化): draw the role's lines. / TODO（死亡回归 / 魔女化）：绘制本职业的提示行。
    }

    /**
     * Local player is Hiro (raw synced role) on a confirmed SparkWitch server, alive, playing, not swallowed.
     * 本地玩家在已确认的 SparkWitch 服务器上是希罗（真实同步身份），存活、参与对局、未被吞下。
     */
    static boolean isActiveHiro(@Nullable ClientPlayerEntity player) {
        return player != null
                && SparkWitchServerConnection.isConfirmedServer()
                && HiroRules.isLivingHiro(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }
}
