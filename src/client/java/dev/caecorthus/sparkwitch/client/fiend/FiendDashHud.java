package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendParticipation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Draws the moment Fiend's owner-only Dash line ({@link FiendDashHudRules}). Reads only the local player's synced role,
 * the all-player {@link FiendMomentWorldComponent} and its Dash cooldown, which the server sends with a real value only
 * to the moment Fiend and the client counts down locally for display; the server alone decides a Dash. Called by
 * {@link FiendClient} only on a confirmed SparkWitch server. Role-owned presentation: never the action bar and never
 * the {@code gui.sparkwitch.skills} panel.
 * 绘制时刻中魔人仅本人可见的疾驰行（{@link FiendDashHudRules}）。只读取本地玩家同步的职业、全员同步的
 * {@link FiendMomentWorldComponent} 及其疾驰冷却；服务端只向时刻中的魔人发送真实冷却值，客户端在本地倒数用于展示，
 * 疾驰是否生效只由服务端决定。仅在已确认的 SparkWitch 服务器上由 {@link FiendClient} 调用。属于职业自有展示：
 * 绝不使用动作栏，也绝不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class FiendDashHud {
    private FiendDashHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the moment-owner check (real role + synced owner).
        // 先做开销最小的判断：其他所有客户端都在时刻拥有者检查处返回（真实职业 + 同步的拥有者）。
        boolean momentFiend = FiendParticipation.isMomentFiend(player);
        if (!momentFiend) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!FiendDashHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, momentFiend,
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        FiendDashHudRules.Line line = FiendDashHudRules.line(
                FiendMomentWorldComponent.get(client.world).dashCooldownTicks());
        // The corner row is taken by the shared witch skill line (defensive: a Fiend has no witch skill id) or by the
        // Saint's Karma line, which any killer of a Hellfire Saint can carry, drawn flush in the corner.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：魔人没有魔女技能 id），或被圣徒业报行占用（任何在地狱火
        // 期间击杀圣徒的人都可能带有，紧贴角落绘制）。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        Text text = text(line, line.seconds());
        int measured = renderer.getWidth(text(line, FiendDashHudRules.measuredSeconds(line.seconds())));
        int width = context.getScaledWindowWidth();
        int x = FiendDashHudRules.rowX(width, renderer.getWidth(text));
        int y = FiendDashHudRules.rowY(width, context.getScaledWindowHeight(), measured, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, text, x, y, line.color());
    }

    private static Text text(FiendDashHudRules.Line line, int seconds) {
        if (line.keyHint()) {
            return Text.translatable(line.key(), SparkWitchClient.abilityKeyText());
        }
        return line.hasSeconds() ? Text.translatable(line.key(), seconds) : Text.translatable(line.key());
    }
}
