package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCooldowns;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.List;

/**
 * Role-owned presentation: the Seeker's bottom-right status line (car state, cooldown and reason, battery, camera, mark
 * countdown). It reads only the local player's own owner-synced {@code sparkwitch:seeker_status} and the synced
 * vanilla item cooldown; the server stays authoritative and other clients never receive these values. The line never
 * names a player, hides under F1 or without Wathe's HUD, and yields to the CCTV/possession overlay. It is never part of
 * the witch skill inventory panel.
 * 职业自有展示：搜寻者右下角状态行（小车状态、冷却及原因、电量、摄像头、标记倒计时）。只读取本地玩家自己的、
 * 仅同步给拥有者的 {@code sparkwitch:seeker_status} 与已同步的原版物品冷却；服务端保持权威，其他客户端永远收不到这些值。
 * 该行从不显示玩家名字，F1 或 Wathe HUD 隐藏时不显示，并让位于 CCTV/附身叠加层。它从不属于魔女技能背包面板。
 */
public final class SeekerStatusHud {
    private static boolean registered;

    private SeekerStatusHud() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register(SeekerStatusHud::sparkwitch$render);
    }

    private static void sparkwitch$render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow both.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；两者都要遵循。
        boolean watheHud = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean remoteOverlay = SeekerRemoteViewClient.isActive() || SeekerClientState.isInSession();
        if (!SeekerHudRules.visible(SparkWitchServerConnection.isConfirmedServer(), client.options.hudHidden,
                watheHud, SeekerClientState.isSeeker(), GameFunctions.isPlayerPlayingAndAlive(player),
                remoteOverlay)) {
            return;
        }
        if (SeekerClientState.own() == null) {
            return;
        }
        int markTicks = SeekerClientState.markTarget() == null ? 0 : SeekerClientState.markRemainingTicks();
        SeekerHudRules.Snapshot snapshot = new SeekerHudRules.Snapshot(SeekerClientState.carState(),
                SeekerCooldowns.remainingTicks(player), SeekerClientState.cooldownReason(),
                SeekerClientState.carBattery(), SeekerClientState.cameraEntityId() >= 0, markTicks);
        List<SeekerHudRules.Segment> segments = SeekerHudRules.segments(snapshot);
        if (segments.isEmpty()) {
            return;
        }
        Text line = compose(segments);
        TextRenderer renderer = client.textRenderer;
        int width = context.getScaledWindowWidth();
        int x = SeekerHudRules.lineX(width, renderer.getWidth(line));
        int y = SeekerHudRules.lineY(width, context.getScaledWindowHeight(), x, renderer.fontHeight);
        context.drawTextWithShadow(renderer, line, x, y, SeekerHudRules.BASE_COLOR);
    }

    static Text compose(List<SeekerHudRules.Segment> segments) {
        MutableText line = Text.empty();
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) {
                line.append(Text.translatable(SeekerHudRules.SEPARATOR_KEY)
                        .setStyle(color(SeekerHudRules.SEPARATOR_COLOR)));
            }
            SeekerHudRules.Segment segment = segments.get(index);
            Object[] args = segment.args().stream()
                    .map(arg -> arg instanceof SeekerHudRules.Translated nested ? Text.translatable(nested.key()) : arg)
                    .toArray();
            line.append(Text.translatable(segment.key(), args).setStyle(color(segment.color())));
        }
        return line;
    }

    private static Style color(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }
}
